package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Self-service transaction-PIN change — both the mandatory first-time replacement of the admin-assigned starting PIN and any later voluntary change use this same endpoint. */
@Data
public class ChangeAgentPinRequest {

    @NotBlank
    private String currentPin;

    @NotBlank
    @Size(min = 4, max = 10, message = "PIN must be between 4 and 10 characters")
    private String newPin;
}
