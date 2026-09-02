package com.microfi.authentication.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AdminUserEnrollmentService;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.shared.dto.AdminUserResponse;
import com.microfi.shared.dto.CreateAdminUserRequest;
import com.microfi.shared.dto.DeleteAdminUserRequest;
import com.microfi.shared.dto.ResetAdminUserPasswordRequest;
import com.microfi.shared.dto.UpdateAdminUserRoleRequest;
import com.microfi.shared.dto.UpdateAdminUserStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Back-office account management. ADMIN may create/manage anyone anywhere; BRANCH_MANAGER may
 * only create/manage BRANCH_CASHIER accounts within their own branch; BRANCH_CASHIER cannot
 * manage accounts at all.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin User Management", description = "Back-office account management (Admin / Branch Manager / Branch Cashier)")
public class AdminUserManagementController {

    private final AdminUserRepository adminUserRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminUserEnrollmentService adminUserEnrollmentService;
    private final AuditService auditService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create Back-Office Account", description = "ADMIN can create any role anywhere; BRANCH_MANAGER can only create BRANCH_CASHIER within their own branch.")
    public Mono<AdminUserResponse> create(@Valid @RequestBody CreateAdminUserRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminUser callerUser = caller.getAdminUser();

                    if (callerUser.getRole() == AdminRole.BRANCH_MANAGER) {
                        if (request.getRole() != AdminRole.BRANCH_CASHIER) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch managers can only create BRANCH_CASHIER accounts");
                        }
                        if (!Objects.equals(callerUser.getBranchId(), request.getBranchId())) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch managers can only create accounts within their own branch");
                        }
                    }
                    return toResponse(adminUserEnrollmentService.create(request));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping
    @Operation(summary = "List Back-Office Accounts", description = "ADMIN sees every account; BRANCH_MANAGER/BRANCH_CASHIER see only their own branch's accounts.")
    public Flux<AdminUserResponse> list(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(() -> {
                    AdminUser callerUser = caller.getAdminUser();
                    if (callerUser.getRole() == AdminRole.ADMIN) {
                        return adminUserRepository.findAll().stream()
                                .filter(u -> u.getStatus() != AdminUserStatus.DELETED)
                                .toList();
                    }
                    return adminUserRepository.findAll().stream()
                            .filter(u -> Objects.equals(u.getBranchId(), callerUser.getBranchId()))
                            .filter(u -> u.getStatus() != AdminUserStatus.DELETED)
                            .toList();
                }).subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Back-Office Account", description = "ADMIN can view any account; BRANCH_MANAGER/BRANCH_CASHIER only their own branch's.")
    public Mono<AdminUserResponse> get(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminUser target = findOrThrow(id);
                    AdminAccess.requireBranchScope(caller, target.getBranchId());
                    return toResponse(target);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspend / Reactivate Back-Office Account")
    public Mono<AdminUserResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateAdminUserStatusRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    if (request.getStatus() == AdminUserStatus.DELETED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use DELETE /{id}/delete to delete an account, not this endpoint");
                    }
                    AdminUser target = findOrThrow(id);
                    AdminAccess.requireBranchScope(caller, target.getBranchId());
                    if (target.getStatus() == AdminUserStatus.DELETED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has been deleted and can no longer be suspended or reactivated");
                    }
                    target.setStatus(request.getStatus());
                    AdminUser saved = adminUserRepository.save(target);
                    auditTarget(caller, request.getStatus() == AdminUserStatus.SUSPENDED ? "ADMIN_USER_SUSPENDED" : "ADMIN_USER_REACTIVATED",
                            target, "ADMIN_USER_STATUS_CHANGED", request.getStatus().name());
                    return toResponse(saved);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/delete")
    @Operation(summary = "Delete Back-Office Account", description = "Soft-delete with a mandatory audit reason. ADMIN may delete any account; BRANCH_MANAGER may delete a BRANCH_MANAGER or BRANCH_CASHIER account within their own branch (never an ADMIN account, and never their own).")
    public Mono<AdminUserResponse> delete(@PathVariable UUID id, @Valid @RequestBody DeleteAdminUserRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminUser callerUser = caller.getAdminUser();
                    AdminUser target = findOrThrow(id);
                    if (target.getId().equals(callerUser.getId())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
                    }
                    AdminAccess.requireBranchScope(caller, target.getBranchId());
                    if (target.getStatus() == AdminUserStatus.DELETED) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "This account has already been deleted");
                    }
                    target.setStatus(AdminUserStatus.DELETED);
                    target.setDeletionReason(request.getReason());
                    target.setDeletedBy(callerUser.getId());
                    target.setDeletedAt(Instant.now());
                    AdminUser saved = adminUserRepository.save(target);
                    auditTarget(caller, "ADMIN_USER_DELETED", target, "ADMIN_USER_DELETED_REASON", request.getReason());
                    return toResponse(saved);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Change Role / Branch", description = "ADMIN only — unlike creation and status changes, a BRANCH_MANAGER cannot reassign roles or move accounts across branches (that would let them promote their own privileges by proxy).")
    public Mono<AdminUserResponse> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateAdminUserRoleRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminUser target = findOrThrow(id);
                    requireConsistentRoleAndBranch(request.getRole(), request.getBranchId());
                    target.setRole(request.getRole());
                    target.setBranchId(request.getBranchId());
                    return toResponse(adminUserRepository.save(target));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "Reset Password", description = "Back-Office-initiated reset (no current-password confirmation, unlike a self-service change). ADMIN, or BRANCH_MANAGER for accounts in their own branch.")
    public Mono<AdminUserResponse> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetAdminUserPasswordRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminUser target = findOrThrow(id);
                    AdminAccess.requireBranchScope(caller, target.getBranchId());
                    target.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
                    target.setMustChangePassword(true);
                    AdminUser saved = adminUserRepository.save(target);
                    auditTarget(caller, "ADMIN_USER_PASSWORD_RESET", target, "ADMIN_USER_PASSWORD_RESET_DETAIL", null);
                    return toResponse(saved);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * {@code extraParam} (nullable) fills {@code {param1}} in {@code detailsKey}'s template — the
     * target's own login/role always fill {@code {param2}}/{@code {param3}}, spelled out (not just
     * "ADMIN_USER_...") since the event type alone doesn't say whether the affected account was a
     * Branch Manager or a Branch Cashier — same precision gap the actor's own actorRole fixes.
     */
    private void auditTarget(AdminUserDetails caller, String eventType, AdminUser target, String detailsKey, String extraParam) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType(eventType)
                .actorType(AuditActorType.ADMIN)
                .actorId(caller.getAdminUser().getId())
                .actorLabel(caller.getAdminUser().getLogin())
                .actorRole(caller.getAdminUser().getRole())
                .branchId(target.getBranchId())
                .targetAdminUserId(target.getId())
                .detailsKey(detailsKey)
                .detailsParam1(extraParam)
                .detailsParam2(target.getLogin())
                .detailsParam3(target.getRole().name())
                .build());
    }

    private void requireConsistentRoleAndBranch(AdminRole role, UUID branchId) {
        if (role == AdminRole.ADMIN && branchId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ADMIN accounts must not be branch-scoped");
        }
        if (role != AdminRole.ADMIN && branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, role + " accounts must be assigned a branch");
        }
    }

    private AdminUser findOrThrow(UUID id) {
        return adminUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found: " + id));
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
                .deletionReason(user.getDeletionReason())
                .deletedAt(user.getDeletedAt())
                .build();
    }
}
