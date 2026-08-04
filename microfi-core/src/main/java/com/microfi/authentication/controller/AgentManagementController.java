package com.microfi.authentication.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.shared.dto.AgentResponse;
import com.microfi.shared.dto.RegisterRequest;
import com.microfi.shared.dto.UpdateAgentStatusRequest;
import com.microfi.transactions.service.EscrowService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enroll Agent", description = "Creates a new agent account with hashed PIN. Employee code and IMEI must each be unique; branch must exist. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<AgentResponse> register(@Valid @RequestBody RegisterRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, request.getBranchId());
                    return Mono.fromCallable(() -> {
                        if (agentRepository.existsByEmployeeCode(request.getEmployeeCode())) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Agent with code '" + request.getEmployeeCode() + "' already exists");
                        }
                        if (agentRepository.existsByImei(request.getImei())) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "IMEI '" + request.getImei() + "' is already bound to another agent");
                        }
                        if (!branchRepository.existsById(request.getBranchId())) {
                            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                    "Branch not found: " + request.getBranchId());
                        }

                        Agent agent = Agent.builder()
                                .id(UUID.randomUUID())
                                .employeeCode(request.getEmployeeCode())
                                .fullName(request.getFullName())
                                .phone(request.getPhone())
                                .imei(request.getImei())
                                .branchId(request.getBranchId())
                                .pinHash(passwordEncoder.encode(request.getPin()))
                                .status(AgentStatus.ACTIVE)
                                .build();

                        Agent saved = agentRepository.save(agent);
                        escrowService.createAccountForAgent(saved.getId());
                        return toResponse(saved);
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @GetMapping
    @Operation(summary = "List Agents", description = "Lists all enrolled agents. Any Back-Office role.")
    public Flux<AgentResponse> list(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(agentRepository::findAll)
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspend / Reactivate Agent", description = "Updates an agent's status. Suspending blocks future logins and immediately invalidates any still-valid session token. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<AgentResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateAgentStatusRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    Agent agent = agentRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + id));
                    AdminAccess.requireBranchScope(caller, agent.getBranchId());
                    agent.setStatus(request.getStatus());
                    return toResponse(agentRepository.save(agent));
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
                    return escrowService.applyCeilingOverride(id, request.getTempCeilingXaf(), request.getReason(), request.getValidUntil());
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private AgentResponse toResponse(Agent agent) {
        return AgentResponse.builder()
                .id(agent.getId())
                .employeeCode(agent.getEmployeeCode())
                .fullName(agent.getFullName())
                .phone(agent.getPhone())
                .imei(agent.getImei())
                .branchId(agent.getBranchId())
                .status(agent.getStatus())
                .build();
    }
}
