package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.GeofenceAlertResponse;
import com.microfi.shared.dto.GeofenceRequest;
import com.microfi.shared.dto.GeofenceResponse;
import com.microfi.shared.dto.RouteResponse;
import com.microfi.transactions.service.GeofenceService;
import com.microfi.transactions.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.UUID;

/**
 * UC-11/UC-13 — Back-Office field-tracking dashboard. Mirrors architecture.txt section 11.1:
 * {@code GET /admin/agents/{id}/route}. Geofence configuration/alerts aren't individually tabled
 * in the doc's endpoint list but are required by BR-Fence-01/02 and the {@code geofence}/
 * {@code geofence_alert} schema tables. BR-Route-01: only managers of the agent's branch (or
 * ADMIN) can view the route — same branch-scoping every other admin-on-agent endpoint in this
 * codebase uses.
 */
@RestController
@RequestMapping("/api/v1/admin/agents/{id}")
@RequiredArgsConstructor
@Tag(name = "Field Tracking Dashboard", description = "Historical route visualization and geofence breach alerts (admin/back-office)")
public class AdminTrackingController {

    private final TrackingService trackingService;
    private final GeofenceService geofenceService;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;

    @GetMapping("/route")
    @Operation(summary = "Historical Route", description = "An agent's ordered GPS trail plus that day's collection markers (UC-11, FR-11). BR-Route-01: own branch only, unless ADMIN.")
    public Mono<RouteResponse> route(@PathVariable UUID id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(id));
                    return trackingService.getRoute(id, date);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/geofence")
    @Operation(summary = "Get Geofence", description = "The agent's assigned perimeter polygon (BR-Fence-01). Own branch only, unless ADMIN.")
    public Mono<GeofenceResponse> getGeofence(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(id));
                    return geofenceService.getGeofence(id);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PutMapping("/geofence")
    @Operation(summary = "Set Geofence", description = "Defines/replaces the agent's assigned perimeter polygon, min. 3 vertices (BR-Fence-01). Own branch only, unless ADMIN.")
    public Mono<GeofenceResponse> setGeofence(@PathVariable UUID id, @Valid @RequestBody GeofenceRequest request,
                                               Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    UUID branchId = agentDirectoryService.requireBranchIdForAgent(id);
                    AdminAccess.requireBranchScope(caller, branchId);
                    GeofenceResponse result = geofenceService.setGeofence(id, request);
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.SECURITY)
                            .eventType("AGENT_GEOFENCE_SET")
                            .actorType(AuditActorType.ADMIN)
                            .actorId(caller.getAdminUser().getId())
                            .actorLabel(caller.getAdminUser().getLogin())
                            .actorRole(caller.getAdminUser().getRole())
                            .branchId(branchId)
                            .agentId(id)
                            .detailsKey("AGENT_GEOFENCE_SET_DETAIL")
                            .detailsParam1(String.valueOf(request.getVertices().size()))
                            .build());
                    return result;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/geofence-alerts")
    @Operation(summary = "List Geofence Alerts", description = "Breach history for the agent, most recent first, retained even after resolution (BR-Fence-02). Own branch only, unless ADMIN.")
    public Flux<GeofenceAlertResponse> geofenceAlerts(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(id));
                    return true;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMapMany(ok -> Mono.fromCallable(() -> geofenceService.listAlerts(id))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }
}
