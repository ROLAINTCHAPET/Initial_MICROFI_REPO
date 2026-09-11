package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.AgentMisconductReportResponse;
import com.microfi.transactions.service.AgentMisconductReportAlertBroadcaster;
import com.microfi.transactions.service.AgentMisconductReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Back-Office triage of client-submitted misconduct reports against an agent — ADMIN or
 * BRANCH_MANAGER only (per the explicit "admin or the branch manager" scope; unlike SOS, a
 * cashier has no role in agent conduct oversight). ADMIN sees every branch; BRANCH_MANAGER sees
 * only their own branch's agents. Mirrors AdminSosController's shape exactly.
 */
@RestController
@RequestMapping("/api/v1/admin/misconduct-reports")
@RequiredArgsConstructor
@Tag(name = "Agent Misconduct Reports", description = "Back-Office visibility and review of client-submitted agent misconduct reports")
public class AgentMisconductReportController {

    private final AgentMisconductReportService agentMisconductReportService;
    private final AgentDirectoryService agentDirectoryService;
    private final AgentMisconductReportAlertBroadcaster agentMisconductReportAlertBroadcaster;
    private final AuditService auditService;

    /** Comment-only, no data — purely to keep the connection past Kong's ~60s idle-read timeout, same as AdminSosController's stream. */
    private static final Flux<ServerSentEvent<AgentMisconductReportResponse>> HEARTBEAT =
            Flux.interval(Duration.ofSeconds(20)).map(tick -> ServerSentEvent.<AgentMisconductReportResponse>builder().comment("keep-alive").build());

    @GetMapping
    @Operation(summary = "List Misconduct Reports", description = "Most recent first. ADMIN sees every branch; BRANCH_MANAGER sees only their own branch's agents.")
    public Flux<AgentMisconductReportResponse> list(@RequestParam(defaultValue = "false") boolean unresolvedOnly, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMapMany(caller -> Mono.fromCallable(() -> agentMisconductReportService.list(scopedAgentIds(caller), unresolvedOnly))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream Misconduct Reports", description = "Server-Sent Events push of newly-submitted misconduct reports, for an instant Back-Office notification instead of waiting on the next page load. Branch scope resolved once at connect time and held fixed for the connection's life, same as AdminSosController#stream. ADMIN or BRANCH_MANAGER.")
    public Flux<ServerSentEvent<AgentMisconductReportResponse>> stream(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMapMany(caller -> Mono.fromCallable(() -> Optional.ofNullable(scopedAgentIds(caller)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(agentIds -> agentMisconductReportAlertBroadcaster.stream()
                                .filter(r -> agentIds.isEmpty() || agentIds.get().contains(r.getAgentId()))
                                .map(r -> ServerSentEvent.builder(r).build())))
                .mergeWith(HEARTBEAT);
    }

    @PatchMapping("/{id}/mark-reviewed")
    @Operation(summary = "Mark Misconduct Report Reviewed", description = "Marks the report as handled (retained for audit either way). Own branch only, unless ADMIN.")
    public Mono<AgentMisconductReportResponse> markReviewed(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    UUID agentId = agentMisconductReportService.findReportAgentId(id);
                    UUID branchId = agentDirectoryService.requireBranchIdForAgent(agentId);
                    AdminAccess.requireBranchScope(caller, branchId);
                    AgentMisconductReportResponse result = agentMisconductReportService.markReviewed(id, caller.getAdminUser().getId());
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.SECURITY)
                            .eventType("AGENT_MISCONDUCT_REPORT_REVIEWED")
                            .actorType(AuditActorType.ADMIN)
                            .actorId(caller.getAdminUser().getId())
                            .actorLabel(caller.getAdminUser().getLogin())
                            .actorRole(caller.getAdminUser().getRole())
                            .branchId(branchId)
                            .agentId(agentId)
                            .detailsKey("AGENT_MISCONDUCT_REPORT_REVIEWED_DETAIL")
                            .build());
                    return result;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /** null = unrestricted (ADMIN); otherwise the caller's own branch's agent ids. */
    private List<UUID> scopedAgentIds(AdminUserDetails caller) {
        if (caller.getAdminUser().getRole() == AdminRole.ADMIN) {
            return null;
        }
        return agentDirectoryService.findAgentIdsByBranch(caller.getAdminUser().getBranchId());
    }
}
