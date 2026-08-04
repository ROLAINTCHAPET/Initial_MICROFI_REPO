package com.microfi.mw.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DailyExportRequest(
    @NotBlank String branchId,
    @NotBlank String fileUri,
    @NotBlank String format
) {
}
