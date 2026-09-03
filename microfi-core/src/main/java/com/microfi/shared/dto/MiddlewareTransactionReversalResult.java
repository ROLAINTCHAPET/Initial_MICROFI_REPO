package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of {@code POST /mw/v1/transactions/reverse} — mirrors the middleware's {@code TransactionReversalResult}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareTransactionReversalResult {
    private boolean success;
    private String reversalReference;
}
