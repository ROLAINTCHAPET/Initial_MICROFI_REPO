package com.microfi.shared.dto;

import com.microfi.authentication.domain.AdminRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateAdminUserRoleRequest {

    @NotNull
    private AdminRole role;

    /** Required for BRANCH_MANAGER/BRANCH_CASHIER; must be null for ADMIN (global scope) — same rule as creation. */
    private UUID branchId;
}
