package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    @NotBlank(message = "IANA timezone id is required, e.g. Africa/Douala")
    private String timezone;
}
