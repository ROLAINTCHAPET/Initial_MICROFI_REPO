package com.microfi.transactions.service;

import com.microfi.shared.dto.AgentMisconductReportResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * In-process fan-out of newly-submitted misconduct reports to every connected Back-Office SSE
 * subscriber (see {@code AgentMisconductReportController}'s {@code /stream} endpoint) — same
 * single-JVM reasoning as {@link SosAlertBroadcaster}'s doc, and the same mandatory
 * autoCancel=false fix that broadcaster needed (see its comment for why).
 */
@Component
public class AgentMisconductReportAlertBroadcaster {

    private final Sinks.Many<AgentMisconductReportResponse> sink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    public void publish(AgentMisconductReportResponse event) {
        sink.tryEmitNext(event);
    }

    public Flux<AgentMisconductReportResponse> stream() {
        return sink.asFlux();
    }
}
