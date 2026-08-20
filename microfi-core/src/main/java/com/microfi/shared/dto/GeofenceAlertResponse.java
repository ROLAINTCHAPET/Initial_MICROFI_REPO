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
public class GeofenceAlertResponse {
    private UUID id;
    private UUID agentId;
    private Instant firstDetectedOutsideAt;
    private Instant raisedAt;
    private Instant resolvedAt;
    private boolean active;
}
