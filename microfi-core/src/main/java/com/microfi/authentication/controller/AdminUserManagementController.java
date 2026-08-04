package com.microfi.authentication.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.shared.dto.AdminUserResponse;
import com.microfi.shared.dto.CreateAdminUserRequest;
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
    private final PasswordEncoder passwordEncoder;

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
                    if (request.getRole() == AdminRole.ADMIN && request.getBranchId() != null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ADMIN accounts must not be branch-scoped");
                    }
                    if (request.getRole() != AdminRole.ADMIN && request.getBranchId() == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, request.getRole() + " accounts must be assigned a branch");
                    }
                    if (adminUserRepository.existsByLogin(request.getLogin())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Login '" + request.getLogin() + "' already exists");
                    }

                    AdminUser newUser = AdminUser.builder()
                            .id(UUID.randomUUID())
                            .login(request.getLogin())
                            .passwordHash(passwordEncoder.encode(request.getPassword()))
                            .role(request.getRole())
                            .branchId(request.getBranchId())
                            .build();
                    return toResponse(adminUserRepository.save(newUser));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping
    @Operation(summary = "List Back-Office Accounts", description = "ADMIN sees every account; BRANCH_MANAGER/BRANCH_CASHIER see only their own branch's accounts.")
    public Flux<AdminUserResponse> list(Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(() -> {
                    AdminUser callerUser = caller.getAdminUser();
                    if (callerUser.getRole() == AdminRole.ADMIN) {
                        return adminUserRepository.findAll();
                    }
                    return adminUserRepository.findAll().stream()
                            .filter(u -> Objects.equals(u.getBranchId(), callerUser.getBranchId()))
                            .toList();
                }).subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspend / Reactivate Back-Office Account")
    public Mono<AdminUserResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateAdminUserStatusRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminUser target = adminUserRepository.findById(id)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin user not found: " + id));
                    AdminAccess.requireBranchScope(caller, target.getBranchId());
                    target.setStatus(request.getStatus());
                    return toResponse(adminUserRepository.save(target));
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private AdminUserResponse toResponse(AdminUser user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .login(user.getLogin())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .status(user.getStatus())
                .build();
    }
}
