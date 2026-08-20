package com.microfi.shared.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** UC-16 §6.1: how many collections the agent's app currently has queued locally, not yet synced. */
@Data
public class AgentSyncStatusRequest {

    @NotNull
    @PositiveOrZero
    private Integer pendingCount;
}
