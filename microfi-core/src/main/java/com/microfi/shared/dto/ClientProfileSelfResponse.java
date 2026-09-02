package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** UC-20: My Profile — identity and booklet token status, read-only from the client's perspective. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileSelfResponse {
    private UUID id;
    private String mfiMemberNo;
    private String fullName;
    private String phone;
    private UUID branchId;
    /** Which MFI this client belongs to — shown persistently so the client can always confirm they're on the right institution's app session. */
    private String mfiName;
    /** NONE (never activated), ACTIVE, or EXPIRED. */
    private String tokenStatus;
    private Instant tokenExpiresAt;
}
