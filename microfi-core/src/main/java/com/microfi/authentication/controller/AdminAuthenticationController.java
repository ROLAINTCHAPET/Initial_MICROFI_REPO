package com.microfi.authentication.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminUser;
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
    private final AuditService auditService;

    @PostMapping("/admin/login")
    @Operation(summary = "Back-Office Login", description = "Authenticates an Admin, Branch Manager or Branch Cashier by login/password. Returns a JWT token.")
    public Mono<AuthResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return adminUserDetailsService.findByUsername(request.getLogin())
                .cast(AdminUserDetails.class)
                .flatMap(adminDetails -> {
                    AdminUser adminUser = adminDetails.getAdminUser();
                    if (adminUser.getStatus() == AdminUserStatus.DELETED) {
                        auditLogin(adminUser, AuditStatus.FAILED, "LOGIN_FAILED_ACCOUNT_DELETED");
                        return Mono.error(new InvalidCredentialsException("Admin account has been deleted"));
                    }
                    if (adminUser.getStatus() != AdminUserStatus.ACTIVE) {
                        auditLogin(adminUser, AuditStatus.FAILED, "LOGIN_FAILED_ACCOUNT_SUSPENDED");
                        return Mono.error(new InvalidCredentialsException("Admin account is suspended"));
                    }
                    if (!passwordEncoder.matches(request.getPassword(), adminDetails.getPassword())) {
                        auditLogin(adminUser, AuditStatus.FAILED, "LOGIN_FAILED_INVALID_CREDENTIALS");
                        return Mono.error(new InvalidCredentialsException("Invalid credentials"));
                    }

                    Map<String, Object> extraClaims = new HashMap<>();
                    extraClaims.put("role", adminUser.getRole().name());
                    extraClaims.put("branchId", adminUser.getBranchId());
                    extraClaims.put("mustChangePassword", Boolean.TRUE.equals(adminUser.getMustChangePassword()));
                    extraClaims.put(JwtService.PRINCIPAL_TYPE_CLAIM, JwtService.PRINCIPAL_TYPE_ADMIN_USER);

                    String token = jwtService.generateToken(extraClaims, adminDetails);
                    auditLogin(adminUser, AuditStatus.SUCCESS, "LOGIN_SUCCEEDED");
                    return Mono.just(AuthResponse.builder().token(token).build());
                })
                .onErrorResume(e -> {
                    // A single conditional handler, not two chained onErrorResume operators — the
                    // second would re-fire on whatever the first re-throws, double-writing the
                    // audit entry for every failed login. Every case with a resolved AdminUser
                    // (and therefore a known role) already audited itself above, inside the
                    // flatMap — this branch only covers a login that never resolved to an account
                    // at all, where no role is knowable.
                    if (e instanceof InvalidCredentialsException) {
                        return Mono.error(e);
                    }
                    auditFailedLoginUnknownAccount(request.getLogin(), e.getMessage());
                    return Mono.error(new InvalidCredentialsException("Authentication failed: " + e.getMessage()));
                });
    }

    private void auditLogin(AdminUser adminUser, AuditStatus status, String detailsKey) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("ADMIN_LOGIN")
                .actorType(AuditActorType.ADMIN)
                .actorId(adminUser.getId())
                .actorLabel(adminUser.getLogin())
                .actorRole(adminUser.getRole())
                .branchId(adminUser.getBranchId())
                .detailsKey(detailsKey)
                .status(status)
                .build());
    }

    /** No actorId/actorRole — a login that never resolves to a real account has no role to know, only the attempted username. */
    private void auditFailedLoginUnknownAccount(String attemptedLogin, String reason) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("ADMIN_LOGIN")
                .actorType(AuditActorType.ADMIN)
                .actorLabel(attemptedLogin)
                .detailsKey("LOGIN_FAILED_WITH_REASON")
                .detailsParam1(reason)
                .status(AuditStatus.FAILED)
                .build());
    }
}
