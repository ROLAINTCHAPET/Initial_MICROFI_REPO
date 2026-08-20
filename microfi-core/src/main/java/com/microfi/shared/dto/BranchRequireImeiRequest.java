package com.microfi.shared.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BranchRequireImeiRequest {

    @NotNull
    private Boolean requireImei;
}
