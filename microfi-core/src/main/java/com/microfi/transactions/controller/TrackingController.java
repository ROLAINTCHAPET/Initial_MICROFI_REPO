package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.AgentSyncStatusRequest;
import com.microfi.shared.dto.LocationPingRequest;
import com.microfi.shared.dto.LocationPingResponse;
import com.microfi.shared.dto.SosRequest;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * UC-10/UC-14 — Field Tracking &amp; Security. Mirrors architecture.txt section 11.1:
 * {@code POST /agents/{id}/location}, {@code POST /agents/{id}/sos}. The path {@code id} matches
 * the doc's documented URL shape, but is validated against the authenticated agent's own id
 * (never trusts a client-supplied agent id to report on another agent's behalf) — the same
 * principle {@link CollectionController} applies by resolving the agent from the principal
 * entirely; here the id must stay in the path since the doc nests it under {@code /agents/{id}}.
 */
@RestController
@RequestMapping("/api/v1/agents/{id}")
@RequiredArgsConstructor
@Tag(name = "Field Tracking", description = "Periodic GPS position reporting and emergency SOS")
public class TrackingController {

    private final TrackingService trackingService;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;

    @PostMapping("/location")
    @Operation(summary = "GPS Ping", description = "Periodic location sample while an agent's session is open (UC-10, FR-10). Sampling cadence and the schedule-window stop are the mobile client's responsibility (NFR-09/10).")
    public Mono<LocationPingResponse> location(@PathVariable("id") UUID agentId, @Valid @RequestBody LocationPingRequest request, Mono<Authentication> authenticationMono) {
        return requireSelf(agentId, authenticationMono)
                .then(Mono.fromCallable(() -> trackingService.recordLocation(agentId, request))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @PostMapping("/sos")
    @Operation(summary = "Emergency SOS", description = "Discrete distress alert (UC-14, FR-14). Never gated on GPS availability — accepted best-effort even without a location fix.")
    public Mono<SosResponse> sos(@PathVariable("id") UUID agentId, @Valid @RequestBody SosRequest request, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .flatMap(authentication -> {
                    var agent = ((AgentDetails) authentication.getPrincipal()).getAgent();
                    if (!agent.getId().equals(agentId)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot report location/SOS on behalf of another agent"));
                    }
                    return Mono.fromCallable(() -> trackingService.raiseSos(agentId, request))
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnNext(result -> {
                                AuditLogEntry.AuditLogEntryBuilder entry = AuditLogEntry.builder()
                                        .category(AuditCategory.SECURITY)
                                        .eventType("AGENT_SOS_TRIGGERED")
                                        .actorType(AuditActorType.AGENT)
                                        .actorId(agent.getId())
                                        .actorLabel(agent.getUsername())
                                        .branchId(agent.getBranchId())
                                        .agentId(agent.getId());
                                if (request.getLat() != null) {
                                    entry.detailsKey("SOS_RAISED_WITH_FIX")
                                            .detailsParam1(String.valueOf(request.getLat()))
                                            .detailsParam2(String.valueOf(request.getLon()));
                                } else {
                                    entry.detailsKey("SOS_RAISED_NO_FIX");
                                }
                                auditService.record(entry.build());
                            });
                });
    }

    @PatchMapping("/sync-status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Report Local Sync Queue", description = "UC-16 §6.1: self-reports how many collections are queued locally, not yet synced. The branch's OFJ session cannot close while any agent reports a nonzero count, regardless of reconciliation state — the server has no way to know about a collection until this (or an actual sync) tells it about one.")
    public Mono<Void> reportSyncStatus(@PathVariable("id") UUID agentId, @Valid @RequestBody AgentSyncStatusRequest request, Mono<Authentication> authenticationMono) {
        return requireSelf(agentId, authenticationMono)
                .then(Mono.fromRunnable(() -> agentDirectoryService.updateSyncStatus(agentId, request.getPendingCount()))
                        .subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    private Mono<Void> requireSelf(UUID pathAgentId, Mono<Authentication> authenticationMono) {
        return authenticationMono.flatMap(authentication -> {
            UUID callerAgentId = ((AgentDetails) authentication.getPrincipal()).getAgent().getId();
            if (!callerAgentId.equals(pathAgentId)) {
                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot report location/SOS on behalf of another agent"));
            }
            return Mono.empty();
        });
    }
}
