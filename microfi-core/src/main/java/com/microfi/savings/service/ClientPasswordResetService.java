package com.microfi.savings.service;

import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.registration.service.TemporaryCredentialGenerator;
import com.microfi.savings.domain.ClientPasswordResetOtp;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.ClientPasswordResetOtpRepository;
import com.microfi.savings.repository.ClientProfileRepository;
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
 * Client self-service "forgotten PIN" (no agent/admin involved) — verified by a one-time SMS
 * code, mirroring {@code AgentPasswordResetService}'s flow exactly but for {@link ClientProfile}'s
 * {@code pinHash} instead of an agent's password, and its own {@link ClientPasswordResetOtp} table
 * rather than sharing the agent one (no cross-module reach into {@code authentication}'s
 * repository, same reasoning as {@code ClientDirectoryService} existing at all).
 */
@Service
@RequiredArgsConstructor
public class ClientPasswordResetService {

    private final ClientProfileRepository clientProfileRepository;
    private final ClientPasswordResetOtpRepository clientPasswordResetOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsGatewayFactory smsGatewayFactory;
    private final TemporaryCredentialGenerator temporaryCredentialGenerator;

    @Value("${client.auth.otp-expiry-minutes:10}")
    private long otpExpiryMinutes;

    @Value("${client.auth.otp-max-attempts:5}")
    private int otpMaxAttempts;

    /**
     * Unlike other notification sends in this app, delivery failure here fails the request — the
     * whole point of this endpoint is handing the client a code, so a silent no-op on gateway
     * failure would leave them stuck with no path forward and no error to explain why.
     * <p>
     * An unknown login still resolves to the same generic success — mirroring the delay/shape of a
     * real send — so this endpoint doesn't become a way to enumerate valid client logins. A client
     * who never self-activated (null {@code pinHash}) has nothing to reset either, and is treated
     * the same as an unknown login for the same reason.
     */
    public Mono<Void> requestReset(String login) {
        Optional<ClientProfile> maybeClient = clientProfileRepository.findByLogin(login);
        if (maybeClient.isEmpty() || maybeClient.get().getPinHash() == null) {
            return Mono.empty();
        }
        ClientProfile client = maybeClient.get();
        String otp = temporaryCredentialGenerator.generatePin();

        return Mono.fromRunnable(() -> {
                    ClientPasswordResetOtp record = ClientPasswordResetOtp.builder()
                            .id(UUID.randomUUID())
                            .clientId(client.getId())
                            .otpHash(passwordEncoder.encode(otp))
                            .expiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES))
                            .attempts(0)
                            .createdAt(Instant.now())
                            .build();
                    clientPasswordResetOtpRepository.save(record);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.defer(() -> {
                    String message = "MICROFI: your PIN reset code is " + otp + ". It expires in "
                            + otpExpiryMinutes + " minutes. Ignore this message if you did not request it.";
                    return smsGatewayFactory.getActiveGateway().send(client.getPhone(), message);
                }))
                .flatMap(result -> result.success()
                        ? Mono.empty()
                        : Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "Could not send the reset code, please try again")));
    }

    /** Verifies the code, then sets the new PIN. No login-lockout fields exist on {@link ClientProfile} to clear, unlike the agent flow. */
    public Mono<Void> confirmReset(String login, String otp, String newPin) {
        return Mono.fromCallable(() -> {
                    ClientProfile client = clientProfileRepository.findByLogin(login)
                            .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired code"));

                    ClientPasswordResetOtp record = clientPasswordResetOtpRepository
                            .findTopByClientIdAndConsumedAtIsNullOrderByCreatedAtDesc(client.getId())
                            .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired code"));

                    if (record.getExpiresAt().isBefore(Instant.now())) {
                        throw new InvalidCredentialsException("Invalid or expired code");
                    }
                    if (record.getAttempts() >= otpMaxAttempts) {
                        throw new InvalidCredentialsException("Invalid or expired code");
                    }
                    if (!passwordEncoder.matches(otp, record.getOtpHash())) {
                        record.setAttempts(record.getAttempts() + 1);
                        clientPasswordResetOtpRepository.save(record);
                        throw new InvalidCredentialsException("Invalid or expired code");
                    }

                    record.setConsumedAt(Instant.now());
                    clientPasswordResetOtpRepository.save(record);

                    client.setPinHash(passwordEncoder.encode(newPin));
                    clientProfileRepository.save(client);
                    return client;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
