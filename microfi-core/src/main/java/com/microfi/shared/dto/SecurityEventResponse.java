package com.microfi.shared.dto;

import com.microfi.authentication.domain.SecurityEventType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SecurityEventResponse {
    private UUID id;
    private UUID agentId;
    private String agentLabel;
    private UUID branchId;
    private String branchLabel;
    private SecurityEventType type;
    private Instant createdAt;
    private String detail;
    private Instant resolvedAt;
    private String resolvedByAdminUserLabel;
    private String resolutionReason;
}
