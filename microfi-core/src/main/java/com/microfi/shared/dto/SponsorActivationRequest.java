package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** UC-19: the sponsoring agent identifies the client by the login they set during self-activation. */
@Data
public class SponsorActivationRequest {

    @NotBlank
    private String login;
}
