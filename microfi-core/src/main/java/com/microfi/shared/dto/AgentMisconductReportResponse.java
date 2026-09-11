package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AgentMisconductReportResponse {
    private UUID id;
    private UUID agentId;
    private UUID clientId;
    private String reason;
    private Instant reportedAt;
    /** "PENDING" or "REVIEWED", derived from reviewedAt — mirrors CollectionRejectionRequestResponse's status-as-string pattern. */
    private String status;
    private UUID reviewedBy;
    private Instant reviewedAt;
}
