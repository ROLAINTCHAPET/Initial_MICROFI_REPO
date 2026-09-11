package com.microfi.mw.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LookupMemberRequest(
    @NotBlank String accountNumber
) {
}
