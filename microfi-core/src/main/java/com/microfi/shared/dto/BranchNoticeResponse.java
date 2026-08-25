package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BranchNoticeResponse {
    private UUID id;
    private String message;
    private Instant createdAt;
}
