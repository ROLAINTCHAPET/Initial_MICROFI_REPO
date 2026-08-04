package com.microfi.shared.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.Instant;

@Data
public class CeilingOverrideRequest {

    @Positive(message = "Temporary ceiling must be positive")
    private long tempCeilingXaf;

    @NotBlank(message = "Justification is mandatory (BR-Adj-01)")
    private String reason;

    @NotNull
    @Future(message = "validUntil must be in the future")
    private Instant validUntil;
}
