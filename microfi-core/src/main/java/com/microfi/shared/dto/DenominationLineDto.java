package com.microfi.shared.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class DenominationLineDto {

    @NotNull
    @PositiveOrZero
    private Long faceValueXaf;

    @NotNull
    @PositiveOrZero
    private Integer quantity;
}
