package com.microfi.notifications.gateway;

/** Outcome of a single {@link SmsGateway#send} call. {@code providerReference} is the vendor's own message id (for troubleshooting), null on failure. */
public record SmsSendResult(boolean success, String providerReference, String errorMessage) {
}
