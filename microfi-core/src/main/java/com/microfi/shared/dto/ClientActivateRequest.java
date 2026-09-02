package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * UC-19 step 1: client self-service — enters the account number their own MFI already gave them
 * (the same {@code mfiMemberNo} an admin seeded via POST /admin/clients) and sets their own
 * credentials. Proves membership against MICROFI's own records, not a separate CBS-issued code —
 * this deployment only ever serves the one MFI whose clients live in its database.
 */
@Data
public class ClientActivateRequest {

    @NotBlank
    private String mfiIdentifier;

    @NotBlank
    private String login;

    @NotBlank
    @Pattern(regexp = "\\d{4,6}", message = "PIN must be 4 to 6 digits")
    private String pin;
}
