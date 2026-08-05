package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for both halves of UC-19's two-party activation gate (agent sponsorship and client
 * payment confirmation). {@code status} is {@code AWAITING_PAYMENT} or {@code AWAITING_SPONSORSHIP}
 * while only one side has confirmed, or {@code ACTIVE} once both have and the 365-day booklet
 * token has been issued — the completion fields are only populated in that last case.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientActivationResponse {
    private UUID clientId;
    private String status;
    private Instant tokenExpiresAt;
    private Long agentCommissionXaf;
    private Long mfiShareXaf;
    private String paymentReference;
}
