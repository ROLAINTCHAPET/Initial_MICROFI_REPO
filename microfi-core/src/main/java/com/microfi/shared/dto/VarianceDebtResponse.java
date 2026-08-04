package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VarianceDebtResponse {
    private UUID id;
    private UUID agentId;
    private UUID ofjAgentLineId;
    private long amountXaf;
    private String status;
    private Instant createdAt;
}
