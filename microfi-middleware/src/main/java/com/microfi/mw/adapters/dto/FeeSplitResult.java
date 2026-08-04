package com.microfi.mw.adapters.dto;

public record FeeSplitResult(
    long agentCommissionXaf,
    long mfiShareXaf,
    String reference
) {
}
