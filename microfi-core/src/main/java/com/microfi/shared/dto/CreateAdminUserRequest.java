package com.microfi.shared.dto;

import com.microfi.authentication.domain.AdminRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull
    private AdminRole role;

    /** Required for BRANCH_MANAGER/BRANCH_CASHIER; must be null for ADMIN (global scope). */
    private UUID branchId;
}
