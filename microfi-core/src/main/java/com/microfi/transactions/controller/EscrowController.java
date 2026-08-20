package com.microfi.transactions.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.transactions.service.EscrowService;
import com.microfi.shared.dto.EscrowResponse;
import com.microfi.shared.dto.TopUpRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * UC-03/UC-04 — escrow balance/ceiling visibility and manual top-up. Mirrors architecture.txt
 * section 11.1: {@code GET /agents/{id}/escrow}, {@code POST /agents/{id}/escrow/top-up}.
 */
@RestController
@RequestMapping("/api/v1/agents/{id}/escrow")
@RequiredArgsConstructor
@Tag(name = "Escrow", description = "Agent escrow wallet balance, ceiling and top-up")
public class EscrowController {

    private final EscrowService escrowService;
    private final AgentDirectoryService agentDirectoryService;

    @GetMapping
    @Operation(summary = "Get Escrow Status", description = "Current balance, base ceiling and effective ceiling (including any active override). Any authenticated Back-Office role.")
    public Mono<EscrowResponse> getStatus(@PathVariable("id") UUID agentId) {
        return Mono.fromCallable(() -> escrowService.getStatus(agentId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/top-up")
    @Operation(summary = "Top Up Escrow", description = "Manual cashier top-up (MVP): credits the wallet and raises the ceiling by the same amount, which activates a PENDING_CEILING agent (BR-Escrow-01). ADMIN or BRANCH_MANAGER (own branch only) — this is a funds-moving admin action, not something an agent or plain cashier can trigger on their own escrow.")
    public Mono<EscrowResponse> topUp(@PathVariable("id") UUID agentId, @Valid @RequestBody TopUpRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(agentId));
                    return escrowService.topUp(agentId, request.getAmountXaf(), request.getReference());
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
