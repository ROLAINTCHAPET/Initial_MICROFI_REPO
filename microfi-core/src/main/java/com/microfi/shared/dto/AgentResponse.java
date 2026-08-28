package com.microfi.shared.dto;

import com.microfi.authentication.domain.AgentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AgentResponse {
    private UUID id;
    private String employeeCode;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String imei;
    private UUID branchId;
    private AgentStatus status;
    private boolean pinMustChange;

    /** ADMIN/BRANCH_MANAGER-only — left null by AgentSelfController so an agent never sees their own reset history. */
    private String deviceResetReason;
    private Instant deviceResetAt;

    private String deletionReason;
    private Instant deletedAt;
}
