package com.microfi.events;

import java.time.Instant;

public record AuthEvent(
    EventType eventType,
    String username,
    String imei,
    Instant timestamp
) {
    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE
    }

    public static AuthEvent success(String username, String imei) {
        return new AuthEvent(EventType.LOGIN_SUCCESS, username, imei, Instant.now());
    }

    public static AuthEvent failure(String username, String imei) {
        return new AuthEvent(EventType.LOGIN_FAILURE, username, imei, Instant.now());
    }
}
