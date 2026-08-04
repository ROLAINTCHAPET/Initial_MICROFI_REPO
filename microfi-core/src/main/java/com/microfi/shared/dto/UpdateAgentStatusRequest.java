package com.microfi.shared.dto;

import com.microfi.authentication.domain.AgentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAgentStatusRequest {

    @NotNull
    private AgentStatus status;
}
