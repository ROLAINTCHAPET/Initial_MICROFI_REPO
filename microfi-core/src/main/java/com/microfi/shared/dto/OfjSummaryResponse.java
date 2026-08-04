package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OfjSummaryResponse {
    private UUID sessionId;
    private UUID branchId;
    private LocalDate businessDate;
    private String status;
    private List<OfjAgentLineResponse> agentLines;
}
