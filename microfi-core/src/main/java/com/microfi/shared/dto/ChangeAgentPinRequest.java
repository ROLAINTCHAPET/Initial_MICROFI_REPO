package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Self-service transaction-PIN change — both the mandatory first-time replacement of the admin-assigned starting PIN and any later voluntary change use this same endpoint. */
@Data
public class ChangeAgentPinRequest {

    /** Verified against the existing hash, not shape-checked — its format was already enforced whenever it was originally set. */
    @NotBlank
    private String currentPin;

    /** A PIN is a natural number, never letters. */
    @NotBlank
    @Pattern(regexp = "\\d{4,10}", message = "PIN must be 4 to 10 digits")
    private String newPin;
}
