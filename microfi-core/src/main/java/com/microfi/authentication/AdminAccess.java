package com.microfi.authentication;

import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Authorization helper for Back-Office actions: {@code ADMIN} has global scope, {@code BRANCH_MANAGER}
 * and {@code BRANCH_CASHIER} are confined to their own branch. Used by any controller (in
 * {@code authentication} or another module — this class is {@code authentication}'s public
 * contract for role-gating, the same way {@link AdminUserDetails} and {@link AgentDetails} are)
 * that needs to gate an action to Back-Office roles.
 */
public final class AdminAccess {

    private AdminAccess() {
    }

    /** Resolves the authenticated principal as an admin user with one of the allowed roles, or fails with 403. */
    public static Mono<AdminUserDetails> require(Mono<Authentication> authenticationMono, AdminRole... allowedRoles) {
        return authenticationMono.map(authentication -> {
            if (!(authentication.getPrincipal() instanceof AdminUserDetails adminDetails)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Back-office privileges required");
            }
            AdminRole role = adminDetails.getAdminUser().getRole();
            if (allowedRoles.length > 0 && Arrays.stream(allowedRoles).noneMatch(r -> r == role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires role in: " + Arrays.toString(allowedRoles));
            }
            return adminDetails;
        });
    }

    /** ADMIN has global scope; BRANCH_MANAGER/BRANCH_CASHIER may only act within their own branch. */
    public static void requireBranchScope(AdminUserDetails caller, UUID targetBranchId) {
        AdminUser adminUser = caller.getAdminUser();
        if (adminUser.getRole() == AdminRole.ADMIN) {
            return;
        }
        if (!Objects.equals(adminUser.getBranchId(), targetBranchId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Outside your branch scope");
        }
    }
}
