package com.microfi.shared.dto;

import com.microfi.authentication.domain.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAdminUserRequest {

    @NotBlank
    private String login;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone must be in international format, e.g. +237600000000")
    private String phone;

    @NotNull
    private AdminRole role;

    /** Required for BRANCH_MANAGER/BRANCH_CASHIER; must be null for ADMIN (global scope). */
    private UUID branchId;

    /**
     * Id of the account being replaced, required only when the branch already has an active
     * manager (BRANCH_MANAGER) or has reached its cashier cap (BRANCH_CASHIER). That account is
     * suspended, not deleted, as part of this creation. Ignored for ADMIN and for roles that
     * don't currently conflict.
     */
    private UUID replaceUserId;
}
