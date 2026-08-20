package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Result of {@code POST /mw/v1/transactions/post} — mirrors the middleware's {@code TransactionPostResult}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareTransactionPostResult {
    private boolean success;
    private List<String> postedReferences;
}
