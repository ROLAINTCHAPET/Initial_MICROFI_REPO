package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.PasswordResetOtp;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.PasswordResetOtpRepository;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.registration.service.TemporaryCredentialGenerator;
import com.microfi.shared.exception.InvalidCredentialsException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent self-service "forgotten password" (no admin involved, unlike
 * {@code AgentManagementController#resetPassword}) — verified by a one-time SMS code rather than
 * any human approval, per the explicit product decision to use OTP over a branch-manager-approval
 * routing. Deliberately in its own service rather than folded into {@link AgentDetailsService}:
 * that service's contract is Spring Security lookups plus login-lockout bookkeeping, not a
 * multi-step reset flow with its own state machine.
 */
@Service
@RequiredArgsConstructor
public class AgentPasswordResetService {

    private final AgentRepository agentRepository;
    private final PasswordResetOtpRepository passwordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsGatewayFactory smsGatewayFactory;
    private final TemporaryCredentialGenerator temporaryCredentialGenerator;

    @Value("${agent.auth.otp-expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${agent.auth.otp-max-attempts:5}")
    private int otpMaxAttempts;

    /**
     * Unlike {@code NotificationService}'s SMS sends, delivery failure here fails the request —
     * the whole point of this endpoint is handing the agent a code, so a silent no-op on gateway
     * failure would leave them stuck with no path forward and no error to explain why.
     * <p>
     * An unknown username still resolves to the same generic success — mirroring the delay/shape
     * of a real send — so this endpoint doesn't become a way to enumerate valid agent usernames.
     * The one asymmetry: a genuine SMS-gateway failure for a real agent surfaces as a real error
     * (see above), which a careful prober could in principle use to distinguish "exists" from
     * "doesn't" — accepted as a minor, low-value information leak against the alternative of
     * silently swallowing an operational failure the agent has no other way to learn about.
     */
    public Mono<Void> requestReset(String username) {
        Optional<Agent> maybeAgent = agentRepository.findByUsername(username);
        if (maybeAgent.isEmpty()) {
            return Mono.empty();
        }
        Agent agent = maybeAgent.get();
        String otp = temporaryCredentialGenerator.generatePin();

        return Mono.fromRunnable(() -> {
                    PasswordResetOtp record = PasswordResetOtp.builder()
                            .id(UUID.randomUUID())
                            .agentId(agent.getId())
                            .otpHash(passwordEncoder.encode(otp))
                            .expiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES))
                            .attempts(0)
                            .createdAt(Instant.now())
                            .build();
                    passwordResetOtpRepository.save(record);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> {
                    String message = "MICROFI: your password reset code is " + otp + ". It expires in "
                            + otpExpiryMinutes + " minutes. Ignore this message if you did not request it.";
                    return smsGatewayFactory.getActiveGateway().send(agent.getPhone(), message);
                }))
                .flatMap(result -> result.success()
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Could not send the reset code, please try again")));
    }

    /** Verifies the code, then applies the new password and clears any login lockout — the same unlock bundling as the admin-initiated reset. */
    public Mono<Void> confirmReset(String username, String otp, String newPassword) {
        return Mono.fromCallable(() -> {
                    Agent agent = agentRepository.findByUsername(username)
                            .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired code"));

                    PasswordResetOtp record = passwordResetOtpRepository
                            .findTopByAgentIdAndConsumedAtIsNullOrderByCreatedAtDesc(agent.getId())
                            .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired code"));

                    if (record.getExpiresAt().isBefore(Instant.now())) {
                        throw new InvalidCredentialsException("Invalid or expired code");
                    }
                    if (record.getAttempts() >= otpMaxAttempts) {
                        throw new InvalidCredentialsException("Invalid or expired code");
                    }
                    if (!passwordEncoder.matches(otp, record.getOtpHash())) {
                        record.setAttempts(record.getAttempts() + 1);
                        passwordResetOtpRepository.save(record);
                        throw new InvalidCredentialsException("Invalid or expired code");
                    }

                    record.setConsumedAt(Instant.now());
                    passwordResetOtpRepository.save(record);

                    agent.setPasswordHash(passwordEncoder.encode(newPassword));
                    agent.setFailedPinAttempts(0);
                    agent.setLockedUntil(null);
                    agentRepository.save(agent);
                    return agent;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
