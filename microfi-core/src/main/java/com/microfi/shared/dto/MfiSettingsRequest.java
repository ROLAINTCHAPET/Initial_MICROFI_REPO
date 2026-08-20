package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfiSettingsRequest {

    @NotBlank
    private String name;
}
