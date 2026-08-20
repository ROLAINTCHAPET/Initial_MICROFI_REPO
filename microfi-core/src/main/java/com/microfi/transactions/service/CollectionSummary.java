package com.microfi.transactions.service;

import java.time.Instant;
import java.util.UUID;

/** Cross-module view of a {@code Collection} for {@link CollectionDirectoryService} callers that don't belong in {@code transactions}. */
public record CollectionSummary(UUID id, UUID agentId, UUID clientId, long amountXaf, String locationName, Instant collectedAt) {
}
