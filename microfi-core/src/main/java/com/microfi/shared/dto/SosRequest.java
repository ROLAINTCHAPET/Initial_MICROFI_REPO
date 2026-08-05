package com.microfi.shared.dto;

import lombok.Data;

/** UC-14: unlike the collection GPS gate (BR-05), lat/lon are best-effort — an SOS must never be blocked for a missing fix. */
@Data
public class SosRequest {

    private Double lat;

    private Double lon;
}
