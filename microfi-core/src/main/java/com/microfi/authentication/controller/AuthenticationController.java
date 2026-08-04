package com.microfi.authentication.controller;

import com.microfi.authentication.AgentDetails;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    @Operation(summary = "Agent Login", description = "Authenticates an agent using their employee code, PIN, and device IMEI. Returns a JWT token.")
    public Mono<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return agentDetailsService.findByUsername(request.getEmployeeCode())
                .cast(AgentDetails.class)
                .flatMap(agentDetails -> {
                    // Suspended agents must not be able to open a new session (FR-02)
                    if (agentDetails.getAgent().getStatus() != AgentStatus.ACTIVE) {
                        authEventPublisher.publishFailure(request.getEmployeeCode(), request.getImei());
                        return Mono.error(new InvalidCredentialsException("Agent account is suspended"));
                    }

                    // Check PIN
                    if (!passwordEncoder.matches(request.getPin(), agentDetails.getPassword())) {
                        authEventPublisher.publishFailure(request.getEmployeeCode(), request.getImei());
                        return Mono.error(new InvalidCredentialsException("Invalid PIN"));
                    }

                    // Check IMEI binding (FR-01, BR-Auth-02: mandatory, no soft-login without hardware association)
                    if (!request.getImei().equals(agentDetails.getAgent().getImei())) {
                        authEventPublisher.publishFailure(request.getEmployeeCode(), request.getImei());
                        return Mono.error(new InvalidCredentialsException("Device IMEI does not match registered device"));
                    }

                    // Enforce branch schedule window (UC-01 rule: session limited to schedule window, FR-15)
                    String scheduleViolation = checkScheduleWindow(agentDetails.getAgent().getBranchId());
                    if (scheduleViolation != null) {
                        authEventPublisher.publishFailure(request.getEmployeeCode(), request.getImei());
                        return Mono.error(new InvalidCredentialsException(scheduleViolation));
                    }

                    // Generate Token
                    Map<String, Object> extraClaims = new HashMap<>();
                    extraClaims.put("branchId", agentDetails.getAgent().getBranchId());
                    extraClaims.put("role", "AGENT");
                    extraClaims.put("imei", request.getImei());
                    extraClaims.put(JwtService.PRINCIPAL_TYPE_CLAIM, JwtService.PRINCIPAL_TYPE_AGENT);

                    String jwtToken = jwtService.generateToken(extraClaims, agentDetails);
                    authEventPublisher.publishSuccess(request.getEmployeeCode(), request.getImei());
                    return Mono.just(AuthResponse.builder().token(jwtToken).build());
                })
                .onErrorResume(InvalidCredentialsException.class, Mono::error)
                .onErrorResume(e -> {
                    authEventPublisher.publishFailure(request.getEmployeeCode(), request.getImei());
                    return Mono.error(new InvalidCredentialsException("Authentication failed: " + e.getMessage()));
                });
    }

    /**
     * Returns a rejection message if the agent's branch has a configured schedule window and the
     * current time falls outside it, or {@code null} if the session is allowed. A branch with no
     * (or partially configured) schedule imposes no restriction.
     */
    private String checkScheduleWindow(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        Branch branch = branchRepository.findById(branchId).orElse(null);
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
