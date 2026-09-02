package com.microfi.authentication.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AgentEnrollmentService;
import com.microfi.shared.dto.AgentResponse;
import com.microfi.shared.dto.DeleteAgentRequest;
import com.microfi.shared.dto.RegisterRequest;
import com.microfi.shared.dto.ResetAgentDeviceRequest;
import com.microfi.shared.dto.ResetAgentPasswordRequest;
import com.microfi.shared.dto.UpdateAgentStatusRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.transactions.service.EscrowService;
import com.microfi.transactions.service.OfjService;
import com.microfi.shared.dto.CeilingOverrideRequest;
import com.microfi.shared.dto.EscrowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-02 — Agent Profile Enrollment &amp; Lifecycle. Mirrors architecture.txt section 11.1:
 * {@code GET/POST /admin/agents}, {@code PATCH /admin/agents/{id}/status}. Enrollment and
 * lifecycle changes are ADMIN/BRANCH_MANAGER-only, branch-scoped for managers (BR-Enroll-01).
 */
@RestController
@RequestMapping("/api/v1/admin/agents")
@RequiredArgsConstructor
@Tag(name = "Agent Management", description = "Endpoints for agent onboarding and lifecycle management")
public class AgentManagementController {

    private final AgentRepository agentRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final EscrowService escrowService;
    private final OfjService ofjService;
    private final AgentEnrollmentService agentEnrollmentService;
    private final AuditService auditService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enroll Agent", description = "Creates a new agent account: username/password for login, an admin-assigned starting PIN the agent must replace before their first collection. Username and phone must each be unique; branch must exist. The agent's device is never set here — it binds automatically on their first successful login. Starts PENDING_CEILING (cannot log in) until an admin funds their escrow account (POST /agents/{id}/escrow/top-up), which activates them. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<AgentResponse> register(@Valid @RequestBody RegisterRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, request.getBranchId());
                    return Mono.fromCallable(() -> agentEnrollmentService.enroll(request))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(this::toResponse);
                });
    }

    @GetMapping
    @Operation(summary = "List Agents", description = "Lists all enrolled agents (deleted agents excluded). Any Back-Office role.")
    public Flux<AgentResponse> list(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(() -> agentRepository.findAll().stream()
                                .filter(a -> a.getStatus() != AgentStatus.DELETED)
                                .toList())
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Agent", description = "Single agent detail. Any Back-Office role, own branch only unless ADMIN.")
    public Mono<AgentResponse> get(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    Agent agent = findAgentOrThrow(id);
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    return toResponse(agent);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/{id}/variance-debts")
    @Operation(summary = "Agent Variance Debts", description = "This agent's recorded shortages (FR-17), most recent first. Any Back-Office role, own branch only unless ADMIN.")
    public Flux<VarianceDebtResponse> varianceDebts(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean openOnly,
                                                      Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(() -> {
                    Agent agent = findAgentOrThrow(id);
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    return ofjService.listVarianceDebtsForAgent(id, openOnly);
                }).subscribeOn(Schedulers.boundedElastic()).flatMapMany(Flux::fromIterable));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspend / Reactivate Agent", description = "Updates an agent's status. Suspending blocks future logins and immediately invalidates any still-valid session token. Reactivating (ACTIVE) is only allowed from SUSPENDED, and only once the agent has a configured escrow ceiling greater than zero — a freshly enrolled PENDING_CEILING agent activates automatically via escrow top-up, never through this endpoint. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<AgentResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateAgentStatusRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    if (request.getStatus() == AgentStatus.DELETED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use DELETE /{id}/delete to delete an agent, not this endpoint");
                    }
                    Agent agent = agentRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + id));
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    if (agent.getStatus() == AgentStatus.DELETED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "This agent has been deleted and can no longer be suspended or reactivated");
                    }
                    if (request.getStatus() == AgentStatus.ACTIVE) {
                        if (agent.getStatus() != AgentStatus.SUSPENDED) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Only a suspended agent can be reactivated here; a newly enrolled agent activates automatically once their escrow ceiling is funded");
                        }
                        if (escrowService.getStatus(id).getBaseCeilingXaf() <= 0) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Cannot reactivate: agent has no escrow ceiling configured. Fund their escrow account first");
                        }
                    }
                    agent.setStatus(request.getStatus());
                    Agent saved = agentRepository.save(agent);
                    auditAgent(caller, request.getStatus() == AgentStatus.SUSPENDED ? "AGENT_SUSPENDED" : "AGENT_REACTIVATED",
                            agent, request.getStatus() == AgentStatus.SUSPENDED ? "Agent suspended" : "Agent reactivated");
                    return toResponse(saved);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/delete")
    @Operation(summary = "Delete Agent", description = "Soft-delete with a mandatory audit reason. Blocks future logins and excludes the agent from listings, but the row (and their collection history) is kept. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<AgentResponse> delete(@PathVariable UUID id, @Valid @RequestBody DeleteAgentRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    Agent agent = agentRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + id));
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    if (agent.getStatus() == AgentStatus.DELETED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "This agent has already been deleted");
                    }
                    agent.setStatus(AgentStatus.DELETED);
                    agent.setDeletionReason(request.getReason());
                    agent.setDeletedBy(caller.getAdminUser().getId());
                    agent.setDeletedAt(Instant.now());
                    Agent saved = agentRepository.save(agent);
                    auditAgent(caller, "AGENT_DELETED", agent, "Agent deleted: " + request.getReason());
                    return toResponse(saved);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/device-binding")
    @Operation(summary = "Reset Device Binding", description = "Lost/new-device recovery: clears this agent's login history marker (a required reason is kept on file). Devices are recognized system-wide, not owned by one agent (see Terminal) — an agent normally can't move to a device nobody has ever used before, but clearing this puts the agent back into a first-ever-login state, so their very next login registers whatever new device they use, no code to hand them, nothing transmitted out of band. Does not touch the agent's password or PIN. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<AgentResponse> resetDeviceBinding(@PathVariable UUID id, @Valid @RequestBody ResetAgentDeviceRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    Agent agent = agentRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + id));
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    agent.setImei(null);
                    agent.setDeviceResetReason(request.getReason());
                    agent.setDeviceResetAt(Instant.now());
                    Agent saved = agentRepository.save(agent);
                    auditAgent(caller, "AGENT_DEVICE_RESET", agent, "Device binding reset: " + request.getReason());
                    return toResponse(saved);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "Reset Password", description = "Back-Office-initiated login-password reset (no current-password confirmation, unlike the agent's own self-service change). Also clears any active login lockout, so this doubles as an unlock. Does not touch the transaction PIN. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<AgentResponse> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetAgentPasswordRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    Agent agent = findAgentOrThrow(id);
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    agent.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                    agent.setFailedPinAttempts(0);
                    agent.setLockedUntil(null);
                    Agent saved = agentRepository.save(agent);
                    auditAgent(caller, "AGENT_PASSWORD_RESET", agent, "Password reset by Back-Office");
                    return toResponse(saved);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/ceiling")
    @Operation(summary = "Remote Temporary Ceiling Override", description = "UC-05: temporarily raises an agent's collection ceiling. Requires a mandatory justification (BR-Adj-01). ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<EscrowResponse> overrideCeiling(@PathVariable UUID id, @Valid @RequestBody CeilingOverrideRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    Agent agent = agentRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + id));
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    EscrowResponse result = escrowService.applyCeilingOverride(id, request.getTempCeilingXaf(), request.getReason(), request.getValidUntil());
                    auditAgent(caller, "AGENT_CEILING_WAIVER", agent,
                            "Temporary ceiling waiver: " + request.getTempCeilingXaf() + " XAF until " + request.getValidUntil() + ", reason: " + request.getReason());
                    return result;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private void auditAgent(AdminUserDetails caller, String eventType, Agent agent, String details) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType(eventType)
                .actorType(AuditActorType.ADMIN)
                .actorId(caller.getAdminUser().getId())
                .actorLabel(caller.getAdminUser().getLogin())
                .actorRole(caller.getAdminUser().getRole())
                .branchId(agent.getBranchId())
                .agentId(agent.getId())
                .details(details)
                .build());
    }

    private Agent findAgentOrThrow(UUID id) {
        return agentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + id));
    }

    private AgentResponse toResponse(Agent agent) {
        return AgentResponse.builder()
                .id(agent.getId())
                .employeeCode(agent.getEmployeeCode())
                .username(agent.getUsername())
                .email(agent.getEmail())
                .fullName(agent.getFullName())
                .phone(agent.getPhone())
                .imei(agent.getImei())
                .branchId(agent.getBranchId())
                .status(agent.getStatus())
                .pinMustChange(Boolean.TRUE.equals(agent.getPinMustChange()))
                .deviceResetReason(agent.getDeviceResetReason())
                .deviceResetAt(agent.getDeviceResetAt())
                .deletionReason(agent.getDeletionReason())
                .deletedAt(agent.getDeletedAt())
                .build();
    }
}
