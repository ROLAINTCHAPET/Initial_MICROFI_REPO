package com.microfi.mw.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Dev/test-only: seeds (or updates, if the account number already exists) a row in the mock CBS's own simulated member directory — see {@code MockCbsMember}. A real vendor adapter would never expose this; MICROFI itself never creates CBS customers. */
public record SeedMemberRequest(
    @NotBlank String accountNumber,
    @NotBlank String fullName,
    String email,
    String phone
) {
}
