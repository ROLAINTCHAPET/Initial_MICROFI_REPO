package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RequestCollectionRejectionRequest {

    @NotBlank(message = "A reason is required to request a collection be rejected")
    private String reason;
}
