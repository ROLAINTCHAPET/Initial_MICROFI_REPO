package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Self-service password change — both the mandatory first-time replacement of the admin-assigned starting password and any later voluntary change use this same endpoint. */
@Data
public class ChangeAdminUserPasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
