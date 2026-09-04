package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

/** Whether this agent has confirmed-but-unexported cash ready to push via "End My Day" — see AgentSelfController. */
@Data
@Builder
public class ExportableSummaryResponse {
    private long readyCount;
    private long readyTotalXaf;
}
