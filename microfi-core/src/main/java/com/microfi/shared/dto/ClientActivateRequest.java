package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** UC-19 step 1: client self-service — enters the CBS Activation ID and sets their own credentials. */
@Data
public class ClientActivateRequest {

    @NotBlank
    private String activationId;

    @NotBlank
    private String login;

    @NotBlank
    @Pattern(regexp = "\\d{4,6}", message = "PIN must be 4 to 6 digits")
    private String pin;
}
