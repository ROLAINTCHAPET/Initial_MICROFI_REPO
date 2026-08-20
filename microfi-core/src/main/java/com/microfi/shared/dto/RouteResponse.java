package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** UC-11 — Historical Route Visualization: an agent's ordered GPS trail plus that day's collection markers. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    private UUID agentId;
    private LocalDate date;
    private List<RoutePointDto> points;
    private List<RouteTransactionDto> transactions;
}
