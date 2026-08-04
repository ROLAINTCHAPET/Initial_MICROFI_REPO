package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {
    
    @NotBlank(message = "Employee code is required")
    private String employeeCode;
    
    @NotBlank(message = "PIN is required")
    private String pin;
    
    @NotBlank(message = "IMEI is required for agent binding")
    private String imei;
}
