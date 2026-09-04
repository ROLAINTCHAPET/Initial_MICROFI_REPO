package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionRejectionRequestResponse {
    private UUID id;
    private UUID collectionId;
    private UUID agentId;
    private String reason;
    private long actualAmountXaf;
    private Long expectedAmountXaf;
    private Instant requestedAt;
    private String status;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String decisionReason;
    private boolean hasProof;
}
