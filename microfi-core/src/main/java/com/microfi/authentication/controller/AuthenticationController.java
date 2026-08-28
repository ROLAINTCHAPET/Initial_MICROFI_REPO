package com.microfi.authentication.controller;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.AuthRequest;
import com.microfi.shared.dto.AuthResponse;
import com.microfi.events.AuthEventPublisher;
import com.microfi.shared.exception.InvalidCredentialsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for agent authentication and session management")
public class AuthenticationController {

    private final AgentDetailsService agentDetailsService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher authEventPublisher;
    private final BranchRepository branchRepository;

    @PostMapping("/agent/login")
    @Operation(summary = "Agent Login", description = "Authenticates an agent using their username, password, and device IMEI. Returns a JWT token. The transaction PIN is not involved in login — see POST /collections.")
    public Mono<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return agentDetailsService.findByUsername(request.getUsername())
                .cast(AgentDetails.class)
                .flatMap(agentDetails -> {
                    Agent agent = agentDetails.getAgent();

                    // A SUSPENDED or DELETED agent is blocked from logging in (FR-02). A
                    // PENDING_CEILING agent (escrow not yet funded) may still log in and use the
                    // app — they just can't collect deposits yet, enforced separately at
                    // collection time (see AgentDirectoryService#verifyTransactionPin).
                    if (agent.getStatus() == AgentStatus.DELETED) {
                        authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                        return Mono.error(new InvalidCredentialsException("Agent account has been deleted"));
                    }
                    if (agent.getStatus() == AgentStatus.SUSPENDED) {
                        authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                        return Mono.error(new InvalidCredentialsException("Agent account is suspended"));
                    }

                    // UC-01 §4.1: locked out after too many failed login attempts — rejected before the password is even checked.
                    if (agent.getLockedUntil() != null && agent.getLockedUntil().isAfter(Instant.now())) {
                        authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                        return Mono.error(new ResponseStatusException(HttpStatus.LOCKED,
                                "Too many failed login attempts. Try again after " + DateTimeFormatter.ISO_INSTANT.format(agent.getLockedUntil())));
                    }

                    // Check password
                    if (!passwordEncoder.matches(request.getPassword(), agentDetails.getPassword())) {
                        return Mono.fromRunnable(() -> agentDetailsService.registerFailedLoginAttempt(agent))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.defer(() -> {
                                    authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                                    return Mono.error(new InvalidCredentialsException("Invalid password"));
                                }));
                    }

                    Branch branch = branchRepository.findById(agent.getBranchId()).orElse(null);

                    // Device binding (FR-01, BR-Auth-02). Three cases:
                    //  - already bound (agent.imei set): strict match required, regardless of the
                    //    branch's current setting — a branch turning the requirement off later
                    //    doesn't retroactively unbind an already-enrolled agent.
                    //  - not yet bound, branch requires binding: this login IS the enrollment —
                    //    bind whatever device sent it, so long as one was actually sent.
                    //  - not yet bound, branch doesn't require binding: skip entirely.
                    boolean requiresImei = branch != null && branch.effectiveRequireImei();
                    boolean bindDeviceNow = false;
                    if (agent.getImei() != null) {
                        if (!agent.getImei().equals(request.getImei())) {
                            authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                            return Mono.error(new InvalidCredentialsException("Device IMEI does not match registered device"));
                        }
                    } else if (requiresImei) {
                        if (request.getImei() == null || request.getImei().isBlank()) {
                            authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                            return Mono.error(new InvalidCredentialsException("Device IMEI does not match registered device"));
                        }
                        bindDeviceNow = true;
                    }

                    // Enforce branch schedule window (UC-01 rule: session limited to schedule window, FR-15)
                    String scheduleViolation = checkScheduleWindow(branch);
                    if (scheduleViolation != null) {
                        authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                        return Mono.error(new InvalidCredentialsException(scheduleViolation));
                    }

                    boolean finalBindDeviceNow = bindDeviceNow;
                    return Mono.fromRunnable(() -> {
                                agentDetailsService.resetFailedLoginAttempts(agent);
                                if (finalBindDeviceNow) {
                                    agent.setImei(request.getImei());
                                    agentDetailsService.bindDevice(agent);
                                }
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .then(Mono.defer(() -> {
                                Map<String, Object> extraClaims = new HashMap<>();
                                extraClaims.put("branchId", agent.getBranchId());
                                extraClaims.put("role", "AGENT");
                                extraClaims.put("imei", request.getImei());
                                extraClaims.put(JwtService.PRINCIPAL_TYPE_CLAIM, JwtService.PRINCIPAL_TYPE_AGENT);

                                String jwtToken = jwtService.generateToken(extraClaims, agentDetails);
                                authEventPublisher.publishSuccess(request.getUsername(), request.getImei());
                                return Mono.just(AuthResponse.builder().token(jwtToken).build());
                            }));
                })
                .onErrorResume(e -> {
                    // InvalidCredentialsException/ResponseStatusException (e.g. the 423 lockout
                    // above) already carry the right status/message — passing them downstream
                    // through further onErrorResume operators doesn't exempt them from a later
                    // unconditional one, so the type check has to happen right here.
                    if (e instanceof InvalidCredentialsException || e instanceof ResponseStatusException) {
                        return Mono.error(e);
                    }
                    authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                    return Mono.error(new InvalidCredentialsException("Authentication failed: " + e.getMessage()));
                });
    }

    /**
     * Returns a rejection message if the agent's branch has a configured schedule window and the
     * current time falls outside it, or {@code null} if the session is allowed. A branch with no
     * (or partially configured) schedule imposes no restriction.
     */
    private String checkScheduleWindow(Branch branch) {
        if (branch == null || branch.getOpenTime() == null || branch.getCloseTime() == null || branch.getTimezone() == null) {
            return null;
        }
        LocalTime now = LocalTime.now(ZoneId.of(branch.getTimezone()));
        if (now.isBefore(branch.getOpenTime()) || !now.isBefore(branch.getCloseTime())) {
            return "Outside authorized session hours (" + branch.getOpenTime() + "-" + branch.getCloseTime() + " " + branch.getTimezone() + ")";
        }
        return null;
    }
}
