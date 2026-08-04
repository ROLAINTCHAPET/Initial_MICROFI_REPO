package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.AdminLoginRequest;
import com.microfi.shared.dto.AuthResponse;
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

import java.util.HashMap;
import java.util.Map;

/** Back-office login for Admin / Branch Manager / Branch Cashier accounts (core.admin_user). */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Admin Authentication", description = "Back-office login for Admin / Branch Manager / Branch Cashier accounts")
public class AdminAuthenticationController {

    private final AdminUserDetailsService adminUserDetailsService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/admin/login")
    @Operation(summary = "Back-Office Login", description = "Authenticates an Admin, Branch Manager or Branch Cashier by login/password. Returns a JWT token.")
    public Mono<AuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return adminUserDetailsService.findByUsername(request.getLogin())
                .cast(AdminUserDetails.class)
                .flatMap(adminDetails -> {
                    if (adminDetails.getAdminUser().getStatus() != AdminUserStatus.ACTIVE) {
                        return Mono.error(new InvalidCredentialsException("Admin account is suspended"));
                    }
                    if (!passwordEncoder.matches(request.getPassword(), adminDetails.getPassword())) {
                        return Mono.error(new InvalidCredentialsException("Invalid credentials"));
                    }

                    Map<String, Object> extraClaims = new HashMap<>();
                    extraClaims.put("role", adminDetails.getAdminUser().getRole().name());
                    extraClaims.put("branchId", adminDetails.getAdminUser().getBranchId());
                    extraClaims.put(JwtService.PRINCIPAL_TYPE_CLAIM, JwtService.PRINCIPAL_TYPE_ADMIN_USER);

                    String token = jwtService.generateToken(extraClaims, adminDetails);
                    return Mono.just(AuthResponse.builder().token(token).build());
                })
                .onErrorResume(InvalidCredentialsException.class, Mono::error)
                .onErrorResume(e -> Mono.error(new InvalidCredentialsException("Authentication failed: " + e.getMessage())));
    }
}
