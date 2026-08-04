package com.microfi.mw.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record FeeSplitRequest(
    @NotBlank String memberId,
    @NotBlank String agentId,
    @Positive long amountXaf
) {
}
