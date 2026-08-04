package com.microfi.shared.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** Result of submitting a daily CBS export to the middleware ({@code POST /mw/v1/exports/daily}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiddlewareExportAck {
    private boolean acknowledged;
    private String ackReference;
}
