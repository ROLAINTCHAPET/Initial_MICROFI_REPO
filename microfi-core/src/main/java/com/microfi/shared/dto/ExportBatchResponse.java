package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ExportBatchResponse {
    private UUID id;
    private UUID ofjId;
    private String fileUri;
    private String format;
    private Instant generatedAt;
    private String ackStatus;
}
