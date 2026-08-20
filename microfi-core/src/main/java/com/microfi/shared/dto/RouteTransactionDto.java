package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** UC-11: a transaction marker overlaid on the route map. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteTransactionDto {
    private UUID collectionId;
    private double lat;
    private double lon;
    private String locationName;
    private long amountXaf;
    private Instant collectedAt;
}
