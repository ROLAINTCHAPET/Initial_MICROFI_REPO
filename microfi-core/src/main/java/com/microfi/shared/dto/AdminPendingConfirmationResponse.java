package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Back-Office "En attente" view — a reconciliation line the cashier has physically counted but the agent hasn't confirmed yet. Branch-wide equivalent of {@link PendingReconciliationLineResponse}, which is scoped to one agent's own mobile view. */
@Data
@Builder
public class AdminPendingConfirmationResponse {
    private UUID lineId;
    private UUID agentId;
    private long totalXaf;
    private long collectionCount;
    private Instant lastCountedAt;
}
