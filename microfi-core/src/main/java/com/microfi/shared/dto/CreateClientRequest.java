package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * Admin-side seeding of MICROFI's local client mirror. Stands in for the CBS member-sync job
 * (not yet built, see cbsclient/synchronization) — MICROFI never creates a new customer in the
 * CBS itself, but its own local {@code client_profile} mirror has to be populated somehow.
 */
@Data
public class CreateClientRequest {

    @NotBlank
    private String mfiMemberNo;

    @NotBlank
    private String fullName;

    private String phone;

    private UUID branchId;

    private String cbsRef;
}
