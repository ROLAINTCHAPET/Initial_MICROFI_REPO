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
public class LocationPingResponse {
    private UUID id;
    private UUID agentId;
    private double lat;
    private double lon;
    private Instant recordedAt;
}
