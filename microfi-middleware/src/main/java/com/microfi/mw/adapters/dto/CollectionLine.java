package com.microfi.mw.adapters.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record CollectionLine(
    @NotNull UUID collectionId,
    @NotBlank String memberId,
    @Positive long amountXaf,
    @NotNull Instant collectedAt
) {
}
