package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * Admin-side seeding of MICROFI's local client mirror. Stands in for the CBS member-sync job
 * (not yet built, see cbsclient/synchronization) — MICROFI never creates a new customer in the
 * CBS itself, but its own local {@code client_profile} mirror has to be populated somehow. The
 * resulting row always starts unverified against the CBS ({@code ClientProfile#cbsSyncedAt} null)
 * — see {@code ClientController#markCbsSynced}.
 * <p>
 * {@code login}/{@code pin} are optional: normally a client sets their own credentials later via
 * self-activation (UC-19), but an admin registering a client who already exists in the CBS (the
 * "an automatic refresh missed them" recovery case) can set both here to make the account
 * immediately usable instead of waiting on that separate step. Provide both together or neither.
 */
@Data
public class CreateClientRequest {

    @NotBlank
    private String mfiMemberNo;

    @NotBlank
    private String fullName;

    private String phone;

    private String email;

    private UUID branchId;

    private String cbsRef;

    private String login;

    private String pin;
}
