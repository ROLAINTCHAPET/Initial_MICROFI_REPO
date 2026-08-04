package com.microfi.mw.adapters.dto;

import java.time.Instant;

public record HistoryEntry(
    String reference,
    long amountXaf,
    Instant date,
    String type
) {
}
