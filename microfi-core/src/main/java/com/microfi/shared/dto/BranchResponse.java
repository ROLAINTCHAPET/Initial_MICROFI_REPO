package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
public class BranchResponse {
    private UUID id;
    private String code;
    private String name;
    private LocalTime openTime;
    private LocalTime closeTime;
    private String timezone;
}
