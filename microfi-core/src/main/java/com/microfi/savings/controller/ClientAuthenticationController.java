package com.microfi.savings.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.savings.ClientDetails;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.service.ClientActivationService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.savings.service.ClientPasswordResetService;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.AuthResponse;
import com.microfi.shared.dto.ClientActivateRequest;
import com.microfi.shared.dto.ClientActivationPendingResponse;
import com.microfi.shared.dto.ClientForgotPasswordRequest;
import com.microfi.shared.dto.ClientLoginRequest;
import com.microfi.shared.dto.ClientResetPasswordWithOtpRequest;
import com.microfi.shared.dto.MessageResponse;
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
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

/**
 * UC-19 client-facing authentication surface: self-activation (step 1, sets credentials from a
 * CBS Activation ID) and subsequent login. Sponsorship/fee-split/token issuance (step 2) is
 * agent-authenticated and lives on {@link ClientActivationController}.
 */
@RestController
@RequestMapping("/api/v1/auth/client")
@RequiredArgsConstructor
@Tag(name = "Client Authentication", description = "Client digital booklet self-activation and login")
public class ClientAuthenticationController {

    private final ClientActivationService clientActivationService;
    private final ClientDetailsService clientDetailsService;
    private final ClientPasswordResetService clientPasswordResetService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final MessageResponse FORGOT_PASSWORD_ACK = MessageResponse.builder()
            .message("If that login exists, a reset code has been sent by SMS.")
            .build();

    @PostMapping("/activate")
    @Operation(summary = "Self-Activation Step 1", description = "Client enters their CBS Activation ID and sets their own login/PIN. Awaits agent sponsorship for the fee split and token issuance (UC-19).")
    public Mono<ClientActivationPendingResponse> activate(@Valid @RequestBody ClientActivateRequest request) {
        return Mono.fromCallable(() -> clientActivationService.selfActivate(request))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/login")
    @Operation(summary = "Client Login", description = "Login with the credentials set at self-activation. Allowed even if the booklet token has expired (FR-20/21/22 read-only access).")
    public Mono<AuthResponse> login(@Valid @RequestBody ClientLoginRequest request) {
        return Mono.fromCallable(() -> clientDetailsService.findByUsername(request.getLogin()))
                .subscribeOn(Schedulers.boundedElastic())
                .cast(ClientDetails.class)
                .flatMap(clientDetails -> {
                    ClientProfile client = clientDetails.getClient();
                    if (!passwordEncoder.matches(request.getPin(), clientDetails.getPassword())) {
                        auditClientLogin(client, request.getLogin(), AuditStatus.FAILED, "Login failed: invalid credentials");
                        return Mono.error(new InvalidCredentialsException("Invalid credentials"));
                    }
                    Map<String, Object> extraClaims = new HashMap<>();
                    extraClaims.put(JwtService.PRINCIPAL_TYPE_CLAIM, JwtService.PRINCIPAL_TYPE_CLIENT);
                    String token = jwtService.generateToken(extraClaims, clientDetails);
                    auditClientLogin(client, request.getLogin(), AuditStatus.SUCCESS, "Login succeeded");
                    return Mono.just(AuthResponse.builder().token(token).build());
                })
                .onErrorResume(e -> {
                    // A single conditional handler — InvalidCredentialsException from the flatMap
                    // above is already audited there; re-auditing it here (via a second, unconditional
                    // onErrorResume catching what the first one re-throws) would double-write the entry.
                    if (e instanceof InvalidCredentialsException) {
                        return Mono.error(e);
                    }
                    auditFailedClientLogin(request.getLogin(), e.getMessage());
                    return Mono.error(new InvalidCredentialsException("Authentication failed: " + e.getMessage()));
                });
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Client Forgot Password", description = "Self-service, no agent/admin involved. Sends a one-time SMS code to the client's registered phone. Always returns the same generic acknowledgement, whether or not the login exists, so this can't be used to enumerate valid client logins.")
    public Mono<MessageResponse> forgotPassword(@Valid @RequestBody ClientForgotPasswordRequest request) {
        return clientPasswordResetService.requestReset(request.getLogin())
                .thenReturn(FORGOT_PASSWORD_ACK);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Client Reset PIN With Code", description = "Confirms the SMS code from /forgot-password and sets a new login PIN.")
    public Mono<MessageResponse> resetPassword(@Valid @RequestBody ClientResetPasswordWithOtpRequest request) {
        return clientPasswordResetService.confirmReset(request.getLogin(), request.getOtp(), request.getNewPin())
                .doOnSuccess(v -> auditSelfServicePasswordReset(request.getLogin()))
                .thenReturn(MessageResponse.builder().message("PIN updated. You can now log in.").build());
    }

    /** Self-service — only the login is known at this layer, the OTP flow doesn't otherwise resolve the client's id/branch here. */
    private void auditSelfServicePasswordReset(String login) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("CLIENT_PASSWORD_RESET")
                .actorType(AuditActorType.CLIENT)
                .actorLabel(login)
                .details("PIN reset via self-service SMS code")
                .build());
    }

    private void auditClientLogin(ClientProfile client, String attemptedLogin, AuditStatus status, String details) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("CLIENT_LOGIN")
                .actorType(AuditActorType.CLIENT)
                .actorId(client.getId())
                .actorLabel(attemptedLogin)
                .branchId(client.getBranchId())
                .details(details)
                .status(status)
                .build());
    }

    /** No client resolved — an unrecognized login never reaches a ClientProfile, only the attempted login is known. */
    private void auditFailedClientLogin(String attemptedLogin, String reason) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("CLIENT_LOGIN")
                .actorType(AuditActorType.CLIENT)
                .actorLabel(attemptedLogin)
                .details("Login failed: " + reason)
                .status(AuditStatus.FAILED)
                .build());
    }
}
