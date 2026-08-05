package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One entry from {@code GET /mw/v1/members/{id}/history} (FR-22). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareHistoryEntry {
    private String reference;
    private long amountXaf;
    private Instant date;
    private String type;
}
