package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAdminUserRequest {

    @NotBlank
    private String reason;
}
