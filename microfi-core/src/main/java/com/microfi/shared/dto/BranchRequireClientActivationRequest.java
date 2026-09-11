package com.microfi.shared.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BranchRequireClientActivationRequest {

    @NotNull
    private Boolean requireClientActivation;
}
