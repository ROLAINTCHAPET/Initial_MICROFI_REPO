package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PendingReconciliationLineResponse {
    private UUID lineId;
    private long totalXaf;
    private long collectionCount;
    private Instant lastCountedAt;
}
