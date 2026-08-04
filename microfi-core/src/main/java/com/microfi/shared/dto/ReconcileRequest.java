package com.microfi.shared.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ReconcileRequest {

    @NotNull
    private UUID agentId;

    @NotEmpty(message = "Physical denomination breakdown is required to reconcile (BR-01)")
    @Valid
    private List<DenominationLineDto> physicalDenominationLines;
}
