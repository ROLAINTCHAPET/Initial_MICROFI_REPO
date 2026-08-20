package com.microfi.authentication.service;

import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.shared.dto.CreateAdminUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The back-office-account creation business logic, extracted verbatim from
 * {@code AdminUserManagementController#create} — everything except the BRANCH_MANAGER
 * caller-role narrowing (that's an HTTP-endpoint authorization concern tied to who is allowed to
 * call the endpoint with a given payload, not entity-creation logic, and it doesn't apply when
 * {@code RegistrationApplicationService} provisions from a pre-vetted, ADMIN-approved
 * application). Blocking (JPA), same as the code it was extracted from.
 */
@Service
@RequiredArgsConstructor
public class AdminUserEnrollmentService {

    private final AdminUserRepository adminUserRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUser create(CreateAdminUserRequest request) {
        requireConsistentRoleAndBranch(request.getRole(), request.getBranchId());
        if (adminUserRepository.existsByLogin(request.getLogin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login '" + request.getLogin() + "' already exists");
        }
        if (adminUserRepository.existsByPhone(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone '" + request.getPhone() + "' is already registered to another account");
        }
        if (request.getRole() == AdminRole.BRANCH_MANAGER) {
            replaceIfNeeded(oneActiveManager(request.getBranchId()), request.getReplaceUserId(),
                    "This branch already has a manager");
        } else if (request.getRole() == AdminRole.BRANCH_CASHIER) {
            List<AdminUser> activeCashiers = activeCashiers(request.getBranchId());
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + request.getBranchId()));
            if (activeCashiers.size() >= branch.effectiveMaxCashiers()) {
                AdminUser toReplace = activeCashiers.stream()
                        .filter(u -> u.getId().equals(request.getReplaceUserId()))
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                                "This branch has reached its cashier limit (" + branch.effectiveMaxCashiers()
                                        + "); pick an existing cashier to replace"));
                toReplace.setStatus(AdminUserStatus.SUSPENDED);
                adminUserRepository.save(toReplace);
            }
        }

        AdminUser newUser = AdminUser.builder()
                .id(UUID.randomUUID())
                .login(request.getLogin())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(request.getRole())
                .branchId(request.getBranchId())
                .mustChangePassword(true)
                .build();
        return adminUserRepository.save(newUser);
    }

    private Optional<AdminUser> oneActiveManager(UUID branchId) {
        return adminUserRepository.findByBranchIdAndRole(branchId, AdminRole.BRANCH_MANAGER).stream()
                .filter(u -> u.getStatus() == AdminUserStatus.ACTIVE)
                .findFirst();
    }

    private List<AdminUser> activeCashiers(UUID branchId) {
        return adminUserRepository.findByBranchIdAndRole(branchId, AdminRole.BRANCH_CASHIER).stream()
                .filter(u -> u.getStatus() == AdminUserStatus.ACTIVE)
                .toList();
    }

    /** If {@code existing} is present, suspends it only when {@code replaceUserId} explicitly confirms that exact account; otherwise 409s. */
    private void replaceIfNeeded(Optional<AdminUser> existing, UUID replaceUserId, String conflictMessage) {
        if (existing.isEmpty()) {
            return;
        }
        AdminUser current = existing.get();
        if (!current.getId().equals(replaceUserId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    conflictMessage + " (" + current.getLogin() + "); confirm replaceUserId to replace them");
        }
        current.setStatus(AdminUserStatus.SUSPENDED);
        adminUserRepository.save(current);
    }

    private void requireConsistentRoleAndBranch(AdminRole role, UUID branchId) {
        if (role == AdminRole.ADMIN && branchId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ADMIN accounts must not be branch-scoped");
        }
        if (role != AdminRole.ADMIN && branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, role + " accounts must be assigned a branch");
        }
    }

    /**
     * Read-only availability checks, so a caller (e.g. {@code RegistrationApplicationService})
     * can reject a duplicate at submission time rather than only discovering the conflict when
     * {@link #create} itself is finally called at approval time.
     */
    public boolean isLoginTaken(String login) {
        return adminUserRepository.existsByLogin(login);
    }

    public boolean isPhoneTaken(String phone) {
        return adminUserRepository.existsByPhone(phone);
    }
}
