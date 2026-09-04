package com.microfi.transactions.service;

import com.microfi.shared.dto.SosResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

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

    // autoCancel=false is load-bearing: the plain onBackpressureBuffer() overload defaults to
    // autoCancel=true, which permanently terminates this sink the moment its subscriber count
    // drops to zero (e.g. every admin closing their Back-Office tab between SOS alerts). Once
    // terminated, tryEmitNext() below silently no-ops forever and every later subscriber gets an
    // already-completed stream — no exception, no log, just a broadcaster that looks fine but
    // never delivers again. This broadcaster must outlive any single subscriber's connection.
    private final Sinks.Many<SosResponse> sink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    public void publish(SosResponse event) {
        sink.tryEmitNext(event);
    }

    public Flux<SosResponse> stream() {
        return sink.asFlux();
    }
}
