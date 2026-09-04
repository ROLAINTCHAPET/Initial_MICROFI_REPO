package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RequestCollectionRejectionRequest {

    @NotBlank(message = "A reason is required to request a collection be rejected")
    private String reason;

    /** Optional: what the agent says the amount should actually have been, when the error is about the amount specifically. */
    private Long expectedAmountXaf;
}
