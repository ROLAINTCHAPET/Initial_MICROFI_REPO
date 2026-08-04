package com.microfi.shared.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class VarianceRequest {

    @NotNull
    private UUID ofjAgentLineId;

    private String comment;
}
