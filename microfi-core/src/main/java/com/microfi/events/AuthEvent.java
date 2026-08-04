package com.microfi.events;

import java.time.Instant;

public record AuthEvent(
    EventType eventType,
    String employeeCode,
    String imei,
    Instant timestamp
) {
    public enum EventType {
        LOGIN_SUCCESS,
        LOGIN_FAILURE
    }

    public static AuthEvent success(String employeeCode, String imei) {
        return new AuthEvent(EventType.LOGIN_SUCCESS, employeeCode, imei, Instant.now());
    }

    public static AuthEvent failure(String employeeCode, String imei) {
        return new AuthEvent(EventType.LOGIN_FAILURE, employeeCode, imei, Instant.now());
    }
}
