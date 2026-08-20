package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** UC-01 lost-device recovery: clears an agent's bound device so their next successful login binds a new one. Reason is mandatory and kept on file (Agent#deviceResetReason). */
@Data
public class ResetAgentDeviceRequest {

    @NotBlank
    private String reason;
}
