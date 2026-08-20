package com.microfi.notifications.gateway;

import reactor.core.publisher.Mono;

/**
 * Vendor-neutral contract for outbound SMS delivery. Concrete implementations map this to a
 * specific carrier's API (Orange Developer, MTN Business API, ...). Callers never depend on a
 * vendor's wire format — mirrors {@code CoreBankingAdapter} in the middleware.
 */
public interface SmsGateway {

    /** Unique vendor key this gateway implements, matched against the {@code sms.vendor} property. */
    String vendor();

    Mono<SmsSendResult> send(String recipientPhone, String message);
}
