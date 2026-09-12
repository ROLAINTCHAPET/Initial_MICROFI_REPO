package com.microfi.authentication.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.SecurityEvent;
import com.microfi.authentication.domain.SecurityEventType;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * An alertable, resolvable security-event ledger — see {@link SecurityEvent}'s own doc comment
 * for why this is additive to, not a replacement for, {@code AuditLogEntry}. Every raise here also
 * writes a matching plain audit-log row so {@code /admin/audit-log} stays complete.
 */
@Service
@RequiredArgsConstructor
public class SecurityEventService {

    /** These two mean "the identity presenting this login can no longer be trusted" — every other type is a sync-time chain anomaly, held for review rather than an automatic lockout. */
    private static final Set<SecurityEventType> AGENT_BLOCKING_TYPES =
            Set.of(SecurityEventType.INSTALLATION_MISMATCH, SecurityEventType.DEVICE_MISMATCH);

    private final SecurityEventRepository securityEventRepository;
    private final AgentRepository agentRepository;
    private final AuditService auditService;
    private final AgentDirectoryService agentDirectoryService;

    /**
     * REQUIRES_NEW for the same reason as {@code AuditService#record}: every call site in
     * {@code CollectionService#applyChainRules} raises the event immediately before throwing to
     * reject the record, and {@code CollectionService.recordCollection} is itself
     * {@code @Transactional} — joining that transaction would roll this row back right along with
     * the rejection it's supposed to be evidence of.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SecurityEvent raise(UUID agentId, UUID branchId, SecurityEventType type, String detail) {
        SecurityEvent event = SecurityEvent.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .branchId(branchId)
                .type(type)
                .createdAt(Instant.now())
                .detail(detail)
                .build();
        SecurityEvent saved = securityEventRepository.save(event);

        String actorLabel = agentRepository.findById(agentId).map(Agent::getUsername).orElse(null);
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("SECURITY_EVENT_" + type.name())
                .actorType(AuditActorType.AGENT)
                .actorId(agentId)
                .actorLabel(actorLabel)
                .branchId(branchId)
                .agentId(agentId)
                .detailsKey("SECURITY_EVENT_RAISED")
                .detailsParam1(type.name())
                .detailsParam2(detail)
                .status(AuditStatus.FAILED)
                .build());

        if (AGENT_BLOCKING_TYPES.contains(type)) {
            agentDirectoryService.flipToReconciliationRequired(agentId);
        }
        return saved;
    }

    /** Read-only lookup for the controller to branch-scope-check before calling {@link #resolve}. */
    public SecurityEvent findByIdOrThrow(UUID id) {
        return securityEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Security event not found: " + id));
    }

    /** Admin console read — see SecurityEventController. */
    public List<SecurityEvent> listOpen(UUID branchId) {
        return branchId == null
                ? securityEventRepository.findByResolvedAtIsNullOrderByCreatedAtDesc()
                : securityEventRepository.findByBranchIdAndResolvedAtIsNullOrderByCreatedAtDesc(branchId);
    }

    /**
     * Marks one event resolved. Does NOT itself change {@code Agent.status} — clearing a
     * RECONCILIATION_REQUIRED block stays {@code AgentManagementController#resetDeviceBinding}'s
     * job, so there's exactly one lever to un-block an agent, not two independent ones that could
     * drift apart.
     */
    public SecurityEvent resolve(UUID securityEventId, UUID adminUserId, String reason) {
        SecurityEvent event = securityEventRepository.findById(securityEventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Security event not found: " + securityEventId));
        event.setResolvedAt(Instant.now());
        event.setResolvedByAdminUserId(adminUserId);
        event.setResolutionReason(reason);
        return securityEventRepository.save(event);
    }

    /** Called by AgentManagementController#resetDeviceBinding so clearing the binding automatically closes out whatever open event(s) triggered the block, instead of leaving them for a separate admin action. */
    public void resolveAllOpenForAgent(UUID agentId, UUID adminUserId, String reason) {
        securityEventRepository.findByAgentIdAndResolvedAtIsNull(agentId).forEach(event -> {
            event.setResolvedAt(Instant.now());
            event.setResolvedByAdminUserId(adminUserId);
            event.setResolutionReason(reason);
            securityEventRepository.save(event);
        });
    }
}
