package com.microfi.notifications.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.notifications.service.BroadcastMessageService;
import com.microfi.shared.dto.BroadcastMessageRequest;
import com.microfi.shared.dto.BroadcastMessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * ADMIN/BRANCH_MANAGER announcements to every client or every agent, delivered in-platform
 * (polled by the mobile app) and by SMS. ADMIN may target network-wide or one branch;
 * BRANCH_MANAGER is always forced to their own branch — see {@link BroadcastMessageService#broadcast}.
 */
@RestController
@RequestMapping("/api/v1/admin/broadcasts")
@RequiredArgsConstructor
@Tag(name = "Broadcast Messages", description = "Admin/branch-manager announcements to all clients or all agents, in-platform + SMS")
public class BroadcastMessageController {

    private final BroadcastMessageService broadcastMessageService;
    private final AuditService auditService;

    @PostMapping
    @Operation(summary = "Send Broadcast", description = "Persists the announcement (visible to recipients' mobile app on next poll) and fire-and-forget SMS-blasts every resolved recipient phone. ADMIN or BRANCH_MANAGER.")
    public Mono<BroadcastMessageResponse> send(@Valid @RequestBody BroadcastMessageRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    BroadcastMessageResponse response = broadcastMessageService.broadcast(caller, request);
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.COMPLIANCE)
                            .eventType("BROADCAST_MESSAGE_SENT")
                            .actorType(AuditActorType.ADMIN)
                            .actorId(caller.getAdminUser().getId())
                            .actorLabel(caller.getAdminUser().getLogin())
                            .actorRole(caller.getAdminUser().getRole())
                            .branchId(response.getBranchId())
                            .detailsKey("BROADCAST_MESSAGE_SENT_DETAIL")
                            .detailsParam1(response.getAudience())
                            .detailsParam2(String.valueOf(response.getRecipientCount()))
                            .build());
                    return response;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping
    @Operation(summary = "Broadcast History", description = "Most recent first. ADMIN sees every branch's sends (network-wide included); BRANCH_MANAGER sees only their own branch's.")
    public Flux<BroadcastMessageResponse> history(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMapMany(caller -> Mono.fromCallable(() -> broadcastMessageService.history(caller.getAdminUser().getBranchId(), caller.getAdminUser().getRole() == AdminRole.ADMIN))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }
}
