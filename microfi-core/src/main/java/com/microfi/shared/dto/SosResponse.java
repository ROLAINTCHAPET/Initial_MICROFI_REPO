package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SosResponse {
    private UUID id;
    private UUID agentId;
    private Double lat;
    private Double lon;
    private String locationName;
    private Instant raisedAt;
    private UUID acknowledgedBy;
    private Instant acknowledgedAt;
}
