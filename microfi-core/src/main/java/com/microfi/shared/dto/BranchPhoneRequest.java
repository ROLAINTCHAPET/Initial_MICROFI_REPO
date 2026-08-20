package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BranchPhoneRequest {

    @NotBlank
    private String phone;
}
