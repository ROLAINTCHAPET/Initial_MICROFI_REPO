package com.microfi.shared.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** BR-Fence-01: an administrator defines the agent's assigned perimeter as a polygon (min. 3 vertices). */
@Data
public class GeofenceRequest {

    @Valid
    @Size(min = 3, message = "A geofence polygon needs at least 3 vertices")
    private List<GeofenceVertexDto> vertices;
}
