package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CollectionRejectionRequestResponse {
    private UUID id;
    private UUID collectionId;
    private UUID agentId;
    private String reason;
    private Instant requestedAt;
    private String status;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String decisionReason;
    private boolean hasProof;
}
