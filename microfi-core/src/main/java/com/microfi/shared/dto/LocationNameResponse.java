package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationNameResponse {
    /** Null when reverse geocoding couldn't resolve a name for the given coordinates (best-effort, see GeocodingService). */
    private String locationName;
}
