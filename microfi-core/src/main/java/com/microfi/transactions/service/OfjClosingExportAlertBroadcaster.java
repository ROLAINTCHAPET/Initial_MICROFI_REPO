package com.microfi.transactions.service;

import com.microfi.shared.dto.OfjClosingExportAlertResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * In-process fan-out of scheduled closing-time OFJ export completions to every connected
 * Back-Office SSE subscriber (see {@code OfjExportAlertController}'s {@code /stream} endpoint) —
 * same single-JVM reasoning and the same mandatory autoCancel=false fix as
 * {@link CollectionRejectionAlertBroadcaster}: the plain {@code onBackpressureBuffer()} overload
 * defaults to autoCancel=true, which would permanently terminate this sink the moment every
 * subscriber's tab closes at once, silently killing every future notification with no error
 * anywhere.
 */
@Component
public class OfjClosingExportAlertBroadcaster {

    private final Sinks.Many<OfjClosingExportAlertResponse> sink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    public void publish(OfjClosingExportAlertResponse event) {
        sink.tryEmitNext(event);
    }

    public Flux<OfjClosingExportAlertResponse> stream() {
        return sink.asFlux();
    }
}
