package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterRequest {

    @NotBlank
    private String employeeCode;

    @NotBlank
    private String fullName;

    @NotBlank
    private String phone;

    @NotBlank
    private String imei;

    @NotBlank
    @Size(min = 4, max = 10, message = "PIN must be between 4 and 10 characters")
    private String pin;

    @NotNull
    private UUID branchId;
}
