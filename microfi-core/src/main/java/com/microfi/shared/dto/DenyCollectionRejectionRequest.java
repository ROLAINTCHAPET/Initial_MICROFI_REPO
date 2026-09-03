package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DenyCollectionRejectionRequest {

    @NotBlank(message = "A reason is required to deny a collection rejection request")
    private String reason;
}
