package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.AdminUserResponse;
import com.microfi.shared.dto.AuthResponse;
import com.microfi.shared.dto.ChangeAdminUserPasswordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;

/** The authenticated Back-Office account's own profile and self-service password change (ADMIN / BRANCH_MANAGER / BRANCH_CASHIER). */
@RestController
@RequestMapping("/api/v1/admin/users/me")
@RequiredArgsConstructor
@Tag(name = "Admin User Self-Service", description = "The authenticated back-office account's own profile")
public class AdminUserSelfController {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @GetMapping
    @Operation(summary = "Get My Profile", description = "Resolves the caller's own back-office account from their JWT.")
    public Mono<AdminUserResponse> me(Mono<Authentication> authenticationMono) {
        return authenticationMono.map(authentication -> toResponse(requireCaller(authentication)));
    }

    @PatchMapping("/password")
    @Operation(summary = "Change My Password", description = "Replaces the caller's own login password — used both for the mandatory first-time replacement of an admin-assigned starting password and any later voluntary change. Requires the current password. Returns a fresh session token reflecting the updated must-change-password state.")
    public Mono<AuthResponse> changePassword(@Valid @RequestBody ChangeAdminUserPasswordRequest request, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireCaller)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    if (!passwordEncoder.matches(request.getCurrentPassword(), caller.getPasswordHash())) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
                    }
                    caller.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                    caller.setMustChangePassword(false);
                    AdminUser saved = adminUserRepository.save(caller);

                    Map<String, Object> extraClaims = new HashMap<>();
                    extraClaims.put("role", saved.getRole().name());
                    extraClaims.put("branchId", saved.getBranchId());
                    extraClaims.put("mustChangePassword", false);
                    extraClaims.put(JwtService.PRINCIPAL_TYPE_CLAIM, JwtService.PRINCIPAL_TYPE_ADMIN_USER);
                    String token = jwtService.generateToken(extraClaims, new AdminUserDetails(saved));
                    return AuthResponse.builder().token(token).build();
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private AdminUser requireCaller(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AdminUserDetails adminDetails)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only back-office accounts have a self-profile");
        }
        return adminDetails.getAdminUser();
    }

    private AdminUserResponse toResponse(AdminUser user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .login(user.getLogin())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .status(user.getStatus())
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();
    }
}
