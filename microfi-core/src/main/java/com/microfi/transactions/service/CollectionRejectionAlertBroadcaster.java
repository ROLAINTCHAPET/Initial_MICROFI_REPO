package com.microfi.transactions.service;

import com.microfi.shared.dto.CollectionRejectionRequestResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.util.concurrent.Queues;

/**
 * In-process fan-out of newly-submitted collection-rejection requests to every connected
 * Back-Office SSE subscriber (see {@code CollectionRejectionController}'s {@code /stream}
 * endpoint) — same single-JVM reasoning as {@link SosAlertBroadcaster}'s doc, and the same
 * autoCancel=false fix that broadcaster needed: the plain {@code onBackpressureBuffer()} overload
 * defaults to autoCancel=true, which would permanently terminate this sink the moment every
 * admin's Back-Office tab closes at once, silently killing every future notification with no
 * error anywhere.
 */
@Component
public class CollectionRejectionAlertBroadcaster {

    private final Sinks.Many<CollectionRejectionRequestResponse> sink =
            Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, false);

    public void publish(CollectionRejectionRequestResponse event) {
        sink.tryEmitNext(event);
    }

    public Flux<CollectionRejectionRequestResponse> stream() {
        return sink.asFlux();
    }
}
