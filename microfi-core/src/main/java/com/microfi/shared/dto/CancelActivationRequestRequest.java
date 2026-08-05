package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Admin override for a stuck activation gate — mandatory reason, same pattern as ceiling overrides (BR-Adj-01). */
@Data
public class CancelActivationRequestRequest {

    @NotBlank
    private String reason;
}
