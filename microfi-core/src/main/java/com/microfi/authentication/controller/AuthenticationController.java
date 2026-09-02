package com.microfi.authentication.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.repository.TerminalRepository;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.AgentPasswordResetService;
import com.microfi.authentication.service.JwtService;
import com.microfi.authentication.service.TerminalService;
import com.microfi.shared.dto.AuthRequest;
import com.microfi.shared.dto.AuthResponse;
import com.microfi.shared.dto.ForgotPasswordRequest;
import com.microfi.shared.dto.MessageResponse;
import com.microfi.shared.dto.ResetPasswordWithOtpRequest;
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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for agent authentication and session management")
public class AuthenticationController {

    private final AgentDetailsService agentDetailsService;
    private final AgentPasswordResetService agentPasswordResetService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthEventPublisher authEventPublisher;
    private final BranchRepository branchRepository;
    private final TerminalRepository terminalRepository;
    private final TerminalService terminalService;
    private final AuditService auditService;

    private static final MessageResponse FORGOT_PASSWORD_ACK = MessageResponse.builder()
            .message("If that username exists, a reset code has been sent by SMS.")
            .build();

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
                        auditAgentLogin(agent, request.getUsername(), "LOGIN_FAILED_ACCOUNT_DELETED");
                        return Mono.error(new InvalidCredentialsException("Agent account has been deleted"));
                    }
                    if (agent.getStatus() == AgentStatus.SUSPENDED) {
                        authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                        auditAgentLogin(agent, request.getUsername(), "LOGIN_FAILED_ACCOUNT_SUSPENDED");
                        return Mono.error(new InvalidCredentialsException("Agent account is suspended"));
                    }

                    // UC-01 §4.1: locked out after too many failed login attempts — rejected before the password is even checked.
                    if (agent.getLockedUntil() != null && agent.getLockedUntil().isAfter(Instant.now())) {
                        authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                        auditAgentLogin(agent, request.getUsername(), "LOGIN_FAILED_LOCKED_OUT");
                        return Mono.error(new ResponseStatusException(HttpStatus.LOCKED,
                                "Too many failed login attempts. Try again after " + DateTimeFormatter.ISO_INSTANT.format(agent.getLockedUntil())));
                    }

                    // Check password
                    if (!passwordEncoder.matches(request.getPassword(), agentDetails.getPassword())) {
                        return Mono.fromRunnable(() -> agentDetailsService.registerFailedLoginAttempt(agent))
                                .subscribeOn(Schedulers.boundedElastic())
                                .then(Mono.defer(() -> {
                                    authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                                    auditAgentLogin(agent, request.getUsername(), "LOGIN_FAILED_INVALID_CREDENTIALS");
                                    return Mono.error(new InvalidCredentialsException("Invalid password"));
                                }));
                    }

                    Branch branch = branchRepository.findById(agent.getBranchId()).orElse(null);

                    // Device recognition (FR-01, BR-Auth-02). A device/terminal is a property of
                    // the system, not of any one agent: once a device has been used successfully
                    // once, by anyone, it's recognized and any agent may use it from then on.
                    // Three cases:
                    //  - agent has logged in before, same device as last time (agent.imei matches):
                    //    always fine — no registry lookup needed. This also keeps every already-
                    //    bound agent's routine login working on day one, before their existing
                    //    device has ever been explicitly recorded in the new Terminal table.
                    //  - agent has logged in before, different device: the new device must already
                    //    be a recognized terminal — used successfully by anyone, including this
                    //    agent, before — regardless of the branch's current requireImei setting (a
                    //    branch turning the requirement off later doesn't retroactively loosen an
                    //    already-active agent). Otherwise reject; an admin must resetDeviceBinding
                    //    to let this agent bootstrap a new terminal.
                    //  - agent has never logged in before, branch requires a device: this login
                    //    bootstraps the terminal registry with whatever device sent it, so long as
                    //    one was actually sent — even if the system has never seen it before.
                    //    (If the branch doesn't require a device, skip entirely.)
                    boolean requiresImei = branch != null && branch.effectiveRequireImei();
                    boolean recognizeDeviceNow = false;
                    if (agent.getImei() != null) {
                        boolean sameDeviceAsLastTime = agent.getImei().equals(request.getImei());
                        boolean knownTerminal = sameDeviceAsLastTime
                                || (request.getImei() != null && terminalRepository.findByDeviceId(request.getImei()).isPresent());
                        if (!knownTerminal) {
                            authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                            auditAgentLogin(agent, request.getUsername(), "LOGIN_FAILED_UNRECOGNIZED_DEVICE");
                            return Mono.error(new InvalidCredentialsException("Device IMEI does not match registered device"));
                        }
                        recognizeDeviceNow = true;
                    } else if (requiresImei) {
                        if (request.getImei() == null || request.getImei().isBlank()) {
                            authEventPublisher.publishFailure(request.getUsername(), request.getImei());
                            auditAgentLogin(agent, request.getUsername(), "LOGIN_FAILED_DEVICE_REQUIRED");
                            return Mono.error(new InvalidCredentialsException("Device IMEI does not match registered device"));
                        }
                        recognizeDeviceNow = true;
                    }

                    boolean finalRecognizeDeviceNow = recognizeDeviceNow;
                    return Mono.fromRunnable(() -> {
                                agentDetailsService.resetFailedLoginAttempts(agent);
                                if (finalRecognizeDeviceNow) {
                                    terminalService.recognize(request.getImei(), agent.getId());
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
                                auditAgentLoginSuccess(agent, request.getUsername());
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
                    auditFailedAgentLogin(request.getUsername(), e.getMessage());
                    return Mono.error(new InvalidCredentialsException("Authentication failed: " + e.getMessage()));
                });
    }

    private void auditAgentLoginSuccess(Agent agent, String attemptedUsername) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("AGENT_LOGIN")
                .actorType(AuditActorType.AGENT)
                .actorId(agent.getId())
                .actorLabel(attemptedUsername)
                .branchId(agent.getBranchId())
                .agentId(agent.getId())
                .detailsKey("LOGIN_SUCCEEDED")
                .status(AuditStatus.SUCCESS)
                .build());
    }

    private void auditAgentLogin(Agent agent, String attemptedUsername, String detailsKey) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("AGENT_LOGIN")
                .actorType(AuditActorType.AGENT)
                .actorId(agent.getId())
                .actorLabel(attemptedUsername)
                .branchId(agent.getBranchId())
                .agentId(agent.getId())
                .detailsKey(detailsKey)
                .status(AuditStatus.FAILED)
                .build());
    }

    /** No agent resolved — an unrecognized username never reaches the Agent, only the attempted username is known. */
    private void auditFailedAgentLogin(String attemptedUsername, String reason) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("AGENT_LOGIN")
                .actorType(AuditActorType.AGENT)
                .actorLabel(attemptedUsername)
                .detailsKey("LOGIN_FAILED_WITH_REASON")
                .detailsParam1(reason)
                .status(AuditStatus.FAILED)
                .build());
    }

    @PostMapping("/agent/forgot-password")
    @Operation(summary = "Agent Forgot Password", description = "Self-service, no admin involved (contrast AgentManagementController's admin-initiated reset). Sends a one-time SMS code to the agent's registered phone. Always returns the same generic acknowledgement, whether or not the username exists, so this can't be used to enumerate valid usernames.")
    public Mono<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return agentPasswordResetService.requestReset(request.getUsername())
                .thenReturn(FORGOT_PASSWORD_ACK);
    }

    @PostMapping("/agent/reset-password")
    @Operation(summary = "Agent Reset Password With Code", description = "Confirms the SMS code from /agent/forgot-password and sets a new login password. Also clears any active login lockout.")
    public Mono<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordWithOtpRequest request) {
        return agentPasswordResetService.confirmReset(request.getUsername(), request.getOtp(), request.getNewPassword())
                .doOnSuccess(v -> auditSelfServicePasswordReset(request.getUsername()))
                .thenReturn(MessageResponse.builder().message("Password updated. You can now log in.").build());
    }

    /** Self-service (contrast AgentManagementController's admin-initiated reset, same event type, different actor). Only the username is known at this layer — the OTP flow doesn't otherwise resolve the agent's id/branch here. */
    private void auditSelfServicePasswordReset(String username) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("AGENT_PASSWORD_RESET")
                .actorType(AuditActorType.AGENT)
                .actorLabel(username)
                .detailsKey("PASSWORD_RESET_SELF_SERVICE")
                .build());
    }

}
