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
    /** Current AgentInstallationBinding's installationId, if any — see AgentInstallationBinding's doc comment for why this is tracked separately from imei. Null for an agent who has never logged in with an app build that sends one. */
    private String currentInstallationId;
    private UUID branchId;
    private AgentStatus status;
    private boolean pinMustChange;

    /** ADMIN/BRANCH_MANAGER-only — left null by AgentSelfController so an agent never sees their own reset history. */
    private String deviceResetReason;
    private Instant deviceResetAt;

    private String deletionReason;
    private Instant deletedAt;
}
