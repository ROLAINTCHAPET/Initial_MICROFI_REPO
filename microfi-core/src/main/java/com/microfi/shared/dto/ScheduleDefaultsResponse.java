package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalTime;

@Data
@Builder
public class ScheduleDefaultsResponse {
    private LocalTime openTime;
    private LocalTime closeTime;
    private Instant updatedAt;
}
