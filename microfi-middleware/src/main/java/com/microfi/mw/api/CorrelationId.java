package com.microfi.mw.api;

import java.util.UUID;

/** Every middleware invocation carries a correlation id from Core; generate one if the caller omitted it. */
public final class CorrelationId {

    private CorrelationId() {
    }

    public static String resolve(String headerValue) {
        return (headerValue == null || headerValue.isBlank()) ? UUID.randomUUID().toString() : headerValue;
    }
}
