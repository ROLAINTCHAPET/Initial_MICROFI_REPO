package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * A client's own recent cash collection, sourced from MICROFI directly rather than the CBS —
 * visible immediately after an agent validates it, unlike {@link ClientHistoryEntryResponse}
 * (CBS-backed), which only reflects it once the end-of-day export posts it.
 */
@Data
@Builder
public class ClientRecentCollectionResponse {
    private UUID id;
    private long amountXaf;
    private String locationName;
    private Instant collectedAt;
}
