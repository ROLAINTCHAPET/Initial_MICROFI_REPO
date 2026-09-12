package com.microfi.authentication.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.domain.SecurityEvent;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.SecurityEventService;
import com.microfi.shared.dto.ResolveSecurityEventRequest;
import com.microfi.shared.dto.SecurityEventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin console for the open {@link SecurityEvent} queue — the actionable counterpart to
 * {@code /admin/audit-log}'s write-once timeline (see SecurityEvent's own doc comment). ADMIN sees
 * the whole network; BRANCH_MANAGER is pinned to their own branch, same shape as AuditLogController.
 */
@RestController
@RequestMapping("/api/v1/admin/security-events")
@RequiredArgsConstructor
@Tag(name = "Security Events", description = "Open device/installation-binding mismatches and sync-chain anomalies awaiting admin resolution")
public class SecurityEventController {

    private final SecurityEventService securityEventService;
    private final AgentRepository agentRepository;
    private final BranchRepository branchRepository;
    private final AdminUserRepository adminUserRepository;

    @GetMapping
    @Operation(summary = "List Open Security Events", description = "Unresolved installation/device mismatches and sync-chain anomalies, newest first. ADMIN sees the whole network; BRANCH_MANAGER is pinned to their own branch.")
    public Mono<List<SecurityEventResponse>> listOpen(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    UUID effectiveBranchId = caller.getAdminUser().getRole() == AdminRole.BRANCH_MANAGER
                            ? caller.getAdminUser().getBranchId() : null;
                    return toResponses(securityEventService.listOpen(effectiveBranchId));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve Security Event", description = "Marks one open event resolved with a mandatory reason. Does not itself reactivate a blocked agent — use Reset Device Binding for that (AgentManagementController#resetDeviceBinding), which resolves any open events for that agent automatically. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<SecurityEventResponse> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveSecurityEventRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    SecurityEvent event = securityEventService.findByIdOrThrow(id);
                    AdminAccess.requireBranchScope(caller, event.getBranchId());
                    SecurityEvent resolved = securityEventService.resolve(id, caller.getAdminUser().getId(), request.getReason());
                    return toResponses(List.of(resolved)).get(0);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private List<SecurityEventResponse> toResponses(List<SecurityEvent> events) {
        Map<UUID, String> agentNames = agentRepository.findAllById(events.stream()
                        .map(SecurityEvent::getAgentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Agent::getId, Agent::getFullName));
        Map<UUID, String> branchNames = branchRepository.findAllById(events.stream()
                        .map(SecurityEvent::getBranchId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Branch::getId, Branch::getName));
        Map<UUID, String> adminLabels = adminUserRepository.findAllById(events.stream()
                        .map(SecurityEvent::getResolvedByAdminUserId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AdminUser::getId, AdminUser::getLogin));

        return events.stream()
                .map(event -> SecurityEventResponse.builder()
                        .id(event.getId())
                        .agentId(event.getAgentId())
                        .agentLabel(agentNames.get(event.getAgentId()))
                        .branchId(event.getBranchId())
                        .branchLabel(branchNames.get(event.getBranchId()))
                        .type(event.getType())
                        .createdAt(event.getCreatedAt())
                        .detail(event.getDetail())
                        .resolvedAt(event.getResolvedAt())
                        .resolvedByAdminUserLabel(adminLabels.get(event.getResolvedByAdminUserId()))
                        .resolutionReason(event.getResolutionReason())
                        .build())
                .toList();
    }
}
