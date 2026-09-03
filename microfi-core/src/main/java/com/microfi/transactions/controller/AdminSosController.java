package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.service.SosAlertBroadcaster;
import com.microfi.transactions.service.TrackingService;
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
 * UC-14 — Back-Office SOS console. Previously entirely missing: {@code TrackingController} could
 * record an SOS but nothing ever let an admin see one — "Administrator console flashes a red SOS
 * alert with map and contact options" (UC-14 nominal scenario) had no backing endpoint. ADMIN
 * sees every branch; BRANCH_MANAGER/BRANCH_CASHIER see only their own branch's agents, regardless
 * of an agent's current status (see {@code AgentDirectoryService#findAgentIdsByBranch}).
 */
@RestController
@RequestMapping("/api/v1/admin/sos-events")
@RequiredArgsConstructor
@Tag(name = "SOS Console", description = "Back-Office visibility and acknowledgement of agent distress alerts")
public class AdminSosController {

    private final TrackingService trackingService;
    private final AgentDirectoryService agentDirectoryService;
    private final SosAlertBroadcaster sosAlertBroadcaster;
    private final AuditService auditService;

    /** Comment-only, no data — purely to keep the connection past Kong's ~60s idle-read timeout (kong.yml's `protected` route sets no explicit override, so its OpenResty default applies). */
    private static final Flux<ServerSentEvent<SosResponse>> HEARTBEAT =
            Flux.interval(Duration.ofSeconds(20)).map(tick -> ServerSentEvent.<SosResponse>builder().comment("keep-alive").build());

    @GetMapping
    @Operation(summary = "List SOS Events", description = "Most recent first. ADMIN sees every branch; BRANCH_MANAGER/BRANCH_CASHIER see only their own branch's agents.")
    public Flux<SosResponse> list(@RequestParam(defaultValue = "false") boolean unresolvedOnly, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(() -> trackingService.listSosEvents(scopedAgentIds(caller), unresolvedOnly))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream SOS Events", description = "Server-Sent Events push of newly-raised SOS alerts, for an instant sound/toast in the Back-Office instead of waiting on the next page load or poll. Branch scope (ADMIN vs. own-branch-only) is resolved once at connect time, same as list(), and held fixed for the life of the connection — an agent transferred into the branch after connecting wouldn't be included until the client reconnects.")
    public Flux<ServerSentEvent<SosResponse>> stream(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                // Optional.ofNullable, not the bare nullable List directly: Mono.fromCallable
                // treats a null return as an empty completion rather than "a value that happens to
                // be null" — for ADMIN (unrestricted, scopedAgentIds returns null), that would make
                // this Mono complete empty and flatMapMany below would never even run, silently
                // dropping every event for exactly the role meant to see all of them. Same pitfall
                // this project has hit before with RabbitTemplate's null-on-timeout return.
                .flatMapMany(caller -> Mono.fromCallable(() -> Optional.ofNullable(scopedAgentIds(caller)))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(agentIds -> sosAlertBroadcaster.stream()
                                .filter(sos -> agentIds.isEmpty() || agentIds.get().contains(sos.getAgentId()))
                                .map(sos -> ServerSentEvent.builder(sos).build())))
                .mergeWith(HEARTBEAT);
    }

    @PatchMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge SOS Event", description = "Marks the alert as handled (BR-SOS-02: retained for audit either way). Own branch only, unless ADMIN.")
    public Mono<SosResponse> acknowledge(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    UUID agentId = trackingService.findSosEventAgentId(id);
                    UUID branchId = agentDirectoryService.requireBranchIdForAgent(agentId);
                    AdminAccess.requireBranchScope(caller, branchId);
                    SosResponse result = trackingService.acknowledgeSos(id, caller.getAdminUser().getId());
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.SECURITY)
                            .eventType("SOS_ACKNOWLEDGED")
                            .actorType(AuditActorType.ADMIN)
                            .actorId(caller.getAdminUser().getId())
                            .actorLabel(caller.getAdminUser().getLogin())
                            .actorRole(caller.getAdminUser().getRole())
                            .branchId(branchId)
                            .agentId(agentId)
                            .detailsKey("SOS_ACKNOWLEDGED_DETAIL")
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
