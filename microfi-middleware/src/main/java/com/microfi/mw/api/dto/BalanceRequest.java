package com.microfi.mw.api.dto;

import jakarta.validation.constraints.NotBlank;

public record BalanceRequest(
    @NotBlank String memberId
) {
}
