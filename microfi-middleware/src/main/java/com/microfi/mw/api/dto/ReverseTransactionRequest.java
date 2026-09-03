package com.microfi.mw.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReverseTransactionRequest(
    @NotBlank String reference
) {
}
