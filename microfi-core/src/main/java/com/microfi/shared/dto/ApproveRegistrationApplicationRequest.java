package com.microfi.shared.dto;

import lombok.Data;

import java.util.UUID;

/** Optional — only meaningful when the target branch already has an active manager (BRANCH_MANAGER target) or has reached its cashier cap (BRANCH_CASHIER target), mirroring CreateAdminUserRequest#replaceUserId. */
@Data
public class ApproveRegistrationApplicationRequest {
    private UUID replaceUserId;
}
