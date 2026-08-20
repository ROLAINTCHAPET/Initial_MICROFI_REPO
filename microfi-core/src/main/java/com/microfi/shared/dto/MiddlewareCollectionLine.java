package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One line of {@code POST /mw/v1/transactions/post} — mirrors the middleware's {@code CollectionLine}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareCollectionLine {
    private UUID collectionId;
    private String memberId;
    private long amountXaf;
    private Instant collectedAt;
}
