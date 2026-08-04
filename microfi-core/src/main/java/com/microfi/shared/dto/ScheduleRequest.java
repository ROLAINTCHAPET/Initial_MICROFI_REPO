package com.microfi.shared.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class ScheduleRequest {

    @NotNull
    private LocalTime openTime;

    @NotNull
    private LocalTime closeTime;
}
