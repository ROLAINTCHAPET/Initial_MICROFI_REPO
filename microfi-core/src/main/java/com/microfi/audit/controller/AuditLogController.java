package com.microfi.audit.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditLog;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.shared.dto.AuditLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Security &amp; Administrative Trail — the "who did what, when" view the reference COBAC audit
 * report calls for, but genuinely new to this system (see {@code audit_log}'s own module javadoc).
 * ADMIN sees the whole network; BRANCH_MANAGER is pinned to their own branch regardless of the
 * {@code branchId} query param. BRANCH_CASHIER is intentionally excluded — same sensitivity
 * boundary the app already applies to Settings/Registrations/Tracking.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
@RequiredArgsConstructor
@Tag(name = "Audit Log", description = "Security & administrative trail across every actor type (admin/manager/cashier/agent/client), branch-scoped and date-ranged")
public class AuditLogController {

    private final AuditService auditService;
    private final AgentRepository agentRepository;
    private final BranchRepository branchRepository;

    @GetMapping
    @Operation(summary = "Search Audit Log", description = "Meaningful lifecycle/security events — logins, suspensions, resets, waivers, geofence changes, registration decisions, SOS triggers — never routine reads or high-volume domain data (that's exported from its own table instead). Mandatory from/to bounds the result size. ADMIN or BRANCH_MANAGER only.")
    public Mono<List<AuditLogResponse>> search(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                @RequestParam(required = false) UUID branchId,
                                                @RequestParam(required = false) AuditCategory category,
                                                @RequestParam(required = false) AuditActorType actorType,
                                                Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    UUID effectiveBranchId = effectiveBranchId(caller, branchId);
                    List<AuditLog> logs = auditService.search(from, to, effectiveBranchId, category, actorType);
                    return toResponses(logs);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /** BRANCH_MANAGER can never widen their view past their own branch, whatever the query param says. */
    private UUID effectiveBranchId(AdminUserDetails caller, UUID requestedBranchId) {
        if (caller.getAdminUser().getRole() == AdminRole.BRANCH_MANAGER) {
            return caller.getAdminUser().getBranchId();
        }
        return requestedBranchId;
    }

    private List<AuditLogResponse> toResponses(List<AuditLog> logs) {
        Map<UUID, String> agentNames = agentRepository.findAllById(logs.stream()
                        .map(AuditLog::getAgentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Agent::getId, Agent::getFullName));
        Map<UUID, String> branchNames = branchRepository.findAllById(logs.stream()
                        .map(AuditLog::getBranchId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Branch::getId, Branch::getName));

        return logs.stream()
                .map(log -> AuditLogResponse.builder()
                        .id(log.getId())
                        .occurredAt(log.getOccurredAt())
                        .category(log.getCategory())
                        .eventType(log.getEventType())
                        .actorType(log.getActorType())
                        .actorLabel(log.getActorLabel())
                        .actorRole(log.getActorRole())
                        .branchId(log.getBranchId())
                        .branchLabel(branchNames.get(log.getBranchId()))
                        .agentId(log.getAgentId())
                        .agentLabel(agentNames.get(log.getAgentId()))
                        .details(log.getDetails())
                        .detailsKey(log.getDetailsKey())
                        .detailsParam1(log.getDetailsParam1())
                        .detailsParam2(log.getDetailsParam2())
                        .detailsParam3(log.getDetailsParam3())
                        .status(log.getStatus())
                        .build())
                .toList();
    }
}
