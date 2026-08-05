package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Response to UC-19 step 1 (self-activation): credentials are set, awaiting agent sponsorship. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientActivationPendingResponse {
    private UUID clientId;
    private String mfiMemberNo;
    private String fullName;
    private String message;
}
