package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordWithOtpRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String otp;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;
}
