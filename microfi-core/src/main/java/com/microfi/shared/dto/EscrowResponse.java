package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class EscrowResponse {
    private UUID agentId;
    private long balanceXaf;
    private long baseCeilingXaf;
    private long effectiveCeilingXaf;
    private long cumulativeTodayXaf;
    /** True calendar-day sum, display-only — see CollectionRepository#sumCollectedTodayByAgent's Javadoc for why this is distinct from cumulativeTodayXaf. */
    private long collectedTodayXaf;
    private String activeOverrideReason;
    private Instant overrideValidUntil;
    private Instant updatedAt;
}
