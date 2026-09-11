package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BroadcastMessageResponse {
    private UUID id;
    private String audience;
    /** Null means it was sent network-wide. */
    private UUID branchId;
    private String message;
    private String senderLabel;
    private Instant createdAt;
    /** Only populated on the admin-facing send/list responses — how many phones were SMS'd. Not sent to the mobile poll endpoints. */
    private Integer recipientCount;
}
