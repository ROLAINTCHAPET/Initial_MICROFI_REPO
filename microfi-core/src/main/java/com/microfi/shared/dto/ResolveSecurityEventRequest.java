package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Admin console resolution of an open SecurityEvent — a mandatory reason is kept on file, same pattern as ResetAgentDeviceRequest. */
@Data
public class ResolveSecurityEventRequest {

    @NotBlank
    private String reason;
}
