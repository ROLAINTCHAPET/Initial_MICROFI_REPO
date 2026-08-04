package com.microfi.mw.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record EscrowCreditRequest(
    @NotBlank String agentId,
    @Positive long amountXaf,
    @NotBlank String reference
) {
}
