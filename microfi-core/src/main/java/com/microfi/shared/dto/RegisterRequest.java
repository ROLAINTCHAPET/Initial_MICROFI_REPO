package com.microfi.shared.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterRequest {

    /** Optional HR/business reference — defaults to the username when omitted (see AgentManagementController). */
    private String employeeCode;

    @NotBlank
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone must be in international format, e.g. +237600000000")
    private String phone;

    /** No longer set at enrollment — every agent starts unbound and binds their device automatically on their first successful login (see AuthenticationController#login). */
    private String imei;

    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    /** Admin-assigned starting PIN — the agent is forced to replace it with one of their own before their first collection (Agent#pinMustChange). A PIN is a natural number, never letters. */
    @NotBlank
    @Pattern(regexp = "\\d{4,10}", message = "PIN must be 4 to 10 digits")
    private String pin;

    @NotNull
    private UUID branchId;
}
