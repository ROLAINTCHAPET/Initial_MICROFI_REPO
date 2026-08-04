package com.microfi.shared.dto;

import com.microfi.authentication.domain.AdminUserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAdminUserStatusRequest {

    @NotNull
    private AdminUserStatus status;
}
