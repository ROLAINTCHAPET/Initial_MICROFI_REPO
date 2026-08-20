package com.microfi.authentication.controller;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.shared.dto.AgentResponse;
import com.microfi.shared.dto.BranchResponse;
import com.microfi.shared.dto.ChangeAgentPinRequest;
import com.microfi.shared.dto.RouteResponse;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Gap found while building the mobile client: an agent's JWT carries only their employee code
 * (the {@code sub} claim), never their internal UUID — so there was no way for the app to resolve
 * "who am I" into the id that every other agent-facing endpoint (escrow, route, geofence) actually
 * needs. Mirrors the same principal resolution {@code CollectionController#resolveAgentId} already
 * uses, just exposed as a read-only self-lookup instead of an implicit per-request resolution.
 */
@RestController
@RequestMapping("/api/v1/agents/me")
@RequiredArgsConstructor
@Tag(name = "Agent Self-Service", description = "The authenticated agent's own profile")
public class AgentSelfController {

    private final TrackingService trackingService;
    private final BranchRepository branchRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @Operation(summary = "Get My Profile", description = "Resolves the caller's own agent record from their JWT — id, branch, phone, IMEI, status, whether the transaction PIN still needs to be set — everything the token itself doesn't carry. Agent principals only.")
    public Mono<AgentResponse> me(Mono<Authentication> authenticationMono) {
        return authenticationMono.map(authentication -> {
            Agent agent = requireAgent(authentication);
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
                    .build();
        });
    }

    @GetMapping("/route")
    @Operation(summary = "My Route", description = "The caller's own ordered GPS trail plus that day's collection markers (UC-11, FR-11). Defaults to today (UTC) when no date is given. Agent principals only — the admin/back-office equivalent (any branch, any agent) lives at GET /admin/agents/{id}/route.")
    public Mono<RouteResponse> myRoute(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> trackingService.getRoute(agent.getId(), date != null ? date : LocalDate.now(ZoneOffset.UTC)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/sos")
    @Operation(summary = "My SOS Alerts", description = "The caller's own raised SOS alerts, most recent first, including whether/when Back-Office acknowledged each one (UC-14) — the agent has no other way to know their distress signal was actually seen. Agent principals only.")
    public Flux<SosResponse> mySos(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMapMany(agent -> Mono.fromCallable(() -> trackingService.listSosEvents(List.of(agent.getId()), false))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @GetMapping("/branch")
    @Operation(summary = "My Branch", description = "The caller's own branch — name and contact number, for the mobile app's \"Contact Branch\" action. Agent principals only.")
    public Mono<BranchResponse> myBranch(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> {
                    Branch branch = branchRepository.findById(agent.getBranchId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + agent.getBranchId()));
                    return BranchResponse.builder()
                            .id(branch.getId())
                            .code(branch.getCode())
                            .name(branch.getName())
                            .phone(branch.getPhone())
                            .openTime(branch.getOpenTime())
                            .closeTime(branch.getCloseTime())
                            .timezone(branch.getTimezone())
                            .maxCashiers(branch.effectiveMaxCashiers())
                            .requireImei(branch.effectiveRequireImei())
                            .build();
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/pin")
    @Operation(summary = "Change My Transaction PIN", description = "Replaces the transaction PIN checked on every collection (never the login password). Used both for the mandatory first-time replacement of the admin-assigned starting PIN and any later voluntary change. Agent principals only.")
    public Mono<AgentResponse> changePin(@Valid @RequestBody ChangeAgentPinRequest request, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> {
                    if (!passwordEncoder.matches(request.getCurrentPin(), agent.getPinHash())) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current PIN is incorrect");
                    }
                    if (isWeakPin(request.getNewPin())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "PIN must not be all the same digit or a simple sequence (e.g. 1234)");
                    }
                    agent.setPinHash(passwordEncoder.encode(request.getNewPin()));
                    agent.setPinMustChange(false);
                    agent.setFailedTransactionPinAttempts(0);
                    agent.setTransactionPinLockedUntil(null);
                    Agent saved = agentRepository.save(agent);
                    return AgentResponse.builder()
                            .id(saved.getId())
                            .employeeCode(saved.getEmployeeCode())
                            .username(saved.getUsername())
                            .email(saved.getEmail())
                            .fullName(saved.getFullName())
                            .phone(saved.getPhone())
                            .imei(saved.getImei())
                            .branchId(saved.getBranchId())
                            .status(saved.getStatus())
                            .pinMustChange(Boolean.TRUE.equals(saved.getPinMustChange()))
                            .build();
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /** Rejects the two weakest numeric-PIN shapes: every digit the same, or a simple ascending/descending run. Only meaningful for purely numeric PINs — non-numeric values pass through untouched. */
    private boolean isWeakPin(String pin) {
        if (!pin.chars().allMatch(Character::isDigit)) {
            return false;
        }
        boolean allSame = pin.chars().distinct().count() == 1;
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) != pin.charAt(i - 1) + 1) {
                ascending = false;
            }
            if (pin.charAt(i) != pin.charAt(i - 1) - 1) {
                descending = false;
            }
        }
        return allSame || ascending || descending;
    }

    private Agent requireAgent(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AgentDetails agentDetails)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only agent accounts have a self-profile");
        }
        return agentDetails.getAgent();
    }
}
