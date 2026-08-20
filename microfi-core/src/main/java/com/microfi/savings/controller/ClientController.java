package com.microfi.savings.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.shared.dto.ClientResponse;
import com.microfi.shared.dto.CreateClientRequest;
import com.microfi.shared.dto.UpdateClientStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
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

import java.util.UUID;

/** UC-06 — Multi-Channel Client Lookup. Mirrors architecture.txt section 11.1: {@code GET /clients/lookup}. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Client lookup (agent-facing) and local CBS-mirror seeding (admin)")
public class ClientController {

    private final ClientProfileRepository clientProfileRepository;

    @GetMapping("/api/v1/clients/lookup")
    @Operation(summary = "Multi-Method Client Lookup", description = "Search by membership number, phone or name (QR/ID scans resolve to the same fields client-side), across every client — not limited to the agent's own branch. Which clients an agent may actually collect from is governed by their assigned geofence at collection time (see POST /collections), not by branch. An empty query returns every client, so the agent can browse before typing. Returns candidates for the agent to disambiguate; FR-06.")
    public Flux<ClientResponse> lookup(@RequestParam(defaultValue = "") String query, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(authentication -> {
                    if (!(authentication.getPrincipal() instanceof AgentDetails)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only agent accounts can look up clients");
                    }
                    return query;
                })
                .flatMapMany(q -> Mono.fromCallable(() -> clientProfileRepository.search(q))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @PostMapping("/api/v1/admin/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Seed Local Client Mirror", description = "Registers MICROFI's local mirror of an existing CBS member. Does not create a CBS customer. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<ClientResponse> create(@Valid @RequestBody CreateClientRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, request.getBranchId());
                    return Mono.fromCallable(() -> {
                        if (clientProfileRepository.existsByMfiMemberNo(request.getMfiMemberNo())) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "Client with membership number '" + request.getMfiMemberNo() + "' already exists");
                        }
                        ClientProfile client = ClientProfile.builder()
                                .id(UUID.randomUUID())
                                .mfiMemberNo(request.getMfiMemberNo())
                                .fullName(request.getFullName())
                                .phone(request.getPhone())
                                .branchId(request.getBranchId())
                                .cbsRef(request.getCbsRef())
                                .build();
                        return toResponse(clientProfileRepository.save(client));
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @GetMapping("/api/v1/admin/clients")
    @Operation(summary = "List Clients by Branch", description = "Every client mirrored in a branch. Any Back-Office role, own branch only unless ADMIN.")
    public Flux<ClientResponse> listByBranch(@RequestParam UUID branchId, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> clientProfileRepository.findByBranchId(branchId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                })
                .map(this::toResponse);
    }

    @GetMapping("/api/v1/admin/clients/{id}")
    @Operation(summary = "Get Client", description = "Single client detail. Any Back-Office role, own branch only unless ADMIN.")
    public Mono<ClientResponse> get(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    ClientProfile client = findClientOrThrow(id);
                    AdminAccess.requireBranchScope(caller, client.getBranchId());
                    return toResponse(client);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/api/v1/admin/clients/{id}/status")
    @Operation(summary = "Activate / Deactivate Client Mirror", description = "Toggles the local mirror row's status — does not touch the CBS. ADMIN or BRANCH_MANAGER (own branch only).")
    public Mono<ClientResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateClientStatusRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    ClientProfile client = findClientOrThrow(id);
                    AdminAccess.requireBranchScope(caller, client.getBranchId());
                    client.setStatus(request.getStatus());
                    return toResponse(clientProfileRepository.save(client));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private ClientProfile findClientOrThrow(UUID id) {
        return clientProfileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + id));
    }

    private ClientResponse toResponse(ClientProfile client) {
        return ClientResponse.builder()
                .id(client.getId())
                .mfiMemberNo(client.getMfiMemberNo())
                .fullName(client.getFullName())
                .phone(client.getPhone())
                .branchId(client.getBranchId())
                .status(client.getStatus())
                .build();
    }
}
