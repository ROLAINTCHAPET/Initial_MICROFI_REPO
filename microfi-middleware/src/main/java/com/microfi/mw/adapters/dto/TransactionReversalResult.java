package com.microfi.mw.adapters.dto;

public record TransactionReversalResult(
    boolean success,
    String reversalReference
) {
}
