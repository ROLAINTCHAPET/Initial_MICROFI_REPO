package com.microfi.mw.api.dto;

import com.microfi.mw.adapters.dto.CollectionLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PostTransactionsRequest(
    @NotEmpty @Valid List<CollectionLine> collections
) {
}
