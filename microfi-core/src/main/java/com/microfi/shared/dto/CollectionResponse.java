package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// @Builder alone suppresses the no-args constructor @Data would otherwise generate, which Jackson
// needs to deserialize this — previously invisible since this DTO was only ever serialized *out*
// as an HTTP response body. Now that CollectionRecordDispatcher round-trips it through a RabbitMQ
// RPC reply, it needs to come back the other way too.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionResponse {
    private UUID id;
    private UUID agentId;
    private UUID clientId;
    private String clientName;
    private long amountXaf;
    private double lat;
    private double lon;
    private Float accuracyM;
    private String locationName;
    private Instant collectedAt;
    private String syncStatus;
    private String deviceTxId;
    private List<DenominationLineDto> denominationLines;
    private boolean duplicate;
}
