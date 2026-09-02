package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WriteOffVarianceDebtRequest {

    @NotBlank(message = "A reason is required to write off a variance debt (BR-Var-02)")
    private String reason;
}
