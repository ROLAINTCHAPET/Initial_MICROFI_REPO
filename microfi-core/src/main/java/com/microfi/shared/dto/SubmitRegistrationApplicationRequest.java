package com.microfi.shared.dto;

import com.microfi.registration.domain.RegistrationTargetRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** The "metadata" multipart part of {@code POST /admin/registration-applications} — the five document parts are separate {@code FilePart}s, not part of this JSON body. */
@Data
public class SubmitRegistrationApplicationRequest {

    @NotNull
    private RegistrationTargetRole targetRole;

    @NotNull
    private UUID branchId;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Phone must be in international format, e.g. +237600000000")
    private String phone;

    /** Login handle — becomes RegisterRequest#username or CreateAdminUserRequest#login at provisioning time. */
    @NotBlank
    private String login;

    /** Required only when targetRole == AGENT (AdminUser has no email field). */
    private String email;

    /** Agent-only, optional — mirrors RegisterRequest#employeeCode's default-to-login behavior. */
    private String employeeCode;

    private String nationalIdNumber;

    private String taxIdNumber;

    private String placeOfResidence;

    /** Optional — the 90-day freshness check (see RegistrationApplicationService) only runs when this is actually provided. */
    private LocalDate criminalRecordIssuedDate;
}
