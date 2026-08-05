package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A still-open activation gate, for a branch manager/admin investigating why an agent is blocked. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingActivationRequestResponse {
    private UUID id;
    private UUID clientId;
    private UUID agentId;
    private Instant createdAt;
    private Instant sponsoredAt;
    private Instant paidAt;
}
