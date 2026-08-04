package com.microfi.mw.adapters.dto;

import java.time.Instant;

public record BalanceResult(
    String memberId,
    long balanceXaf,
    Instant asOf
) {
}
