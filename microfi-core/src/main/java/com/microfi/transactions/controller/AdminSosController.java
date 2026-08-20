package com.microfi.transactions.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
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

    @GetMapping
    @Operation(summary = "List SOS Events", description = "Most recent first. ADMIN sees every branch; BRANCH_MANAGER/BRANCH_CASHIER see only their own branch's agents.")
    public Flux<SosResponse> list(@RequestParam(defaultValue = "false") boolean unresolvedOnly, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(() -> trackingService.listSosEvents(scopedAgentIds(caller), unresolvedOnly))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @PatchMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge SOS Event", description = "Marks the alert as handled (BR-SOS-02: retained for audit either way). Own branch only, unless ADMIN.")
    public Mono<SosResponse> acknowledge(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    UUID agentId = trackingService.findSosEventAgentId(id);
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(agentId));
                    return trackingService.acknowledgeSos(id, caller.getAdminUser().getId());
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
