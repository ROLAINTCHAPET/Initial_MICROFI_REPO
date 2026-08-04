package com.microfi.mw.adapters.dto;

public record ExportAckResult(
    boolean acknowledged,
    String ackReference
) {
}
