package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CollectionResponse {
    private UUID id;
    private UUID agentId;
    private UUID clientId;
    private long amountXaf;
    private double lat;
    private double lon;
    private Float accuracyM;
    private Instant collectedAt;
    private String syncStatus;
    private String deviceTxId;
    private List<DenominationLineDto> denominationLines;
    private boolean duplicate;
}
