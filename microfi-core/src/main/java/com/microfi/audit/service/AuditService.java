package com.microfi.audit.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditLog;
import com.microfi.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Write side is intentionally fire-and-forget from the caller's point of view — logging an audit
 * event must never fail or slow down the action it's describing (same "must not block the
 * critical path" rule this codebase already applies to broker publishes): a suspend/reactivate/
 * login/etc. that genuinely succeeded must not roll back or error out just because the audit
 * write itself hit a problem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void record(AuditLogEntry entry) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .id(UUID.randomUUID())
                    .occurredAt(Instant.now())
                    .category(entry.getCategory())
                    .eventType(entry.getEventType())
                    .actorType(entry.getActorType())
                    .actorId(entry.getActorId())
                    .actorLabel(entry.getActorLabel())
                    .actorRole(entry.getActorRole())
                    .branchId(entry.getBranchId())
                    .agentId(entry.getAgentId())
                    .targetAdminUserId(entry.getTargetAdminUserId())
                    .details(entry.getDetails())
                    .status(entry.getStatus())
                    .build());
        } catch (Exception e) {
            log.error("Failed to write audit log entry [{}]: {}", entry.getEventType(), e.getMessage(), e);
        }
    }

    /** {@code to} is exclusive, same convention as {@code CollectionRepository#sumUnreconciledByAgent}'s cutoff. */
    public List<AuditLog> search(Instant from, Instant to, UUID branchId, AuditCategory category, AuditActorType actorType) {
        return auditLogRepository.search(from, to, branchId, category, actorType);
    }
}
