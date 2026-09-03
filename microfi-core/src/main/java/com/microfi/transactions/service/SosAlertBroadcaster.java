package com.microfi.transactions.service;

import com.microfi.shared.dto.SosResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * In-process fan-out of newly-raised SOS alerts to every connected Back-Office SSE subscriber
 * (see AdminSosController's {@code /stream} endpoint) — not the RabbitMQ-based pattern
 * {@code SosGeocodePublisher} uses for reverse-geocoding, since that pattern exists for
 * cross-process delivery and {@code docker-compose.yml} runs exactly one microfi-core instance;
 * there's only ever one JVM that could hold a subscriber. {@code onBackpressureBuffer} tolerates
 * a burst of SOS events arriving faster than a slow client can drain them without dropping any.
 */
@Component
public class SosAlertBroadcaster {

    private final Sinks.Many<SosResponse> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(SosResponse event) {
        sink.tryEmitNext(event);
    }

    public Flux<SosResponse> stream() {
        return sink.asFlux();
    }
}
