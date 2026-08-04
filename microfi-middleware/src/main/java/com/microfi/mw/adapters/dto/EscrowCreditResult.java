package com.microfi.mw.adapters.dto;

public record EscrowCreditResult(
    boolean success,
    long newBalanceXaf,
    String reference
) {
}
