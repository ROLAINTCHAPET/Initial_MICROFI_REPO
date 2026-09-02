package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ClientResetPasswordWithOtpRequest {

    @NotBlank
    private String login;

    @NotBlank
    private String otp;

    @NotBlank
    @Pattern(regexp = "\\d{4,6}", message = "PIN must be 4 to 6 digits")
    private String newPin;
}
