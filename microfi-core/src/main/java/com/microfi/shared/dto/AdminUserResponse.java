package com.microfi.shared.dto;

import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUserStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AdminUserResponse {
    private UUID id;
    private String login;
    private AdminRole role;
    private UUID branchId;
    private AdminUserStatus status;
}
