package com.microfi.savings.controller;

import com.microfi.savings.ClientDetails;
import com.microfi.savings.service.ClientActivationService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.AuthResponse;
import com.microfi.shared.dto.ClientActivateRequest;
import com.microfi.shared.dto.ClientActivationPendingResponse;
import com.microfi.shared.dto.ClientLoginRequest;
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
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

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
                    if (!passwordEncoder.matches(request.getPin(), clientDetails.getPassword())) {
                        return Mono.error(new InvalidCredentialsException("Invalid credentials"));
                    }
                    Map<String, Object> extraClaims = new HashMap<>();
                    extraClaims.put(JwtService.PRINCIPAL_TYPE_CLAIM, JwtService.PRINCIPAL_TYPE_CLIENT);
                    String token = jwtService.generateToken(extraClaims, clientDetails);
                    return Mono.just(AuthResponse.builder().token(token).build());
                })
                .onErrorResume(InvalidCredentialsException.class, Mono::error)
                .onErrorResume(e -> Mono.error(new InvalidCredentialsException("Authentication failed: " + e.getMessage())));
    }
}
