package com.microfi.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AgentMisconductReportRequest {

    @NotNull
    private UUID agentId;

    @NotBlank
    private String reason;
}
