package com.microfi.shared.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GeofenceVertexDto {

    @NotNull
    private Double lat;

    @NotNull
    private Double lon;
}
