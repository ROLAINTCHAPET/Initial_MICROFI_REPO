package com.microfi.transactions.service;

import com.microfi.shared.dto.CollectionRejectionRequestResponse;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.UUID;

class CollectionRejectionAlertBroadcasterTest {

    @Test
    void publishedEventReachesAnAlreadySubscribedStream() {
        CollectionRejectionAlertBroadcaster broadcaster = new CollectionRejectionAlertBroadcaster();
        CollectionRejectionRequestResponse event = CollectionRejectionRequestResponse.builder()
                .id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        StepVerifier.create(broadcaster.stream().take(1))
                .then(() -> broadcaster.publish(event))
                .expectNext(event)
                .verifyComplete();
    }

    @Test
    void eachSubscriberReceivesEventsPublishedAfterItSubscribed() {
        CollectionRejectionAlertBroadcaster broadcaster = new CollectionRejectionAlertBroadcaster();
        CollectionRejectionRequestResponse first = CollectionRejectionRequestResponse.builder()
                .id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();
        CollectionRejectionRequestResponse second = CollectionRejectionRequestResponse.builder()
                .id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        StepVerifier.create(broadcaster.stream().take(2))
                .then(() -> broadcaster.publish(first))
                .expectNext(first)
                .then(() -> broadcaster.publish(second))
                .expectNext(second)
                .verifyComplete();
    }

    // Regression guard for the exact bug found live in SosAlertBroadcaster: the plain
    // onBackpressureBuffer() overload defaults to autoCancel=true, which permanently terminates
    // the sink once its subscriber count drops to zero (every admin closing their Back-Office tab
    // between rejection requests). A later subscriber must still receive events published after it
    // connects, even though nobody was listening for a while in between.
    @Test
    void survivesDroppingToZeroSubscribersBeforeALaterOneConnects() {
        CollectionRejectionAlertBroadcaster broadcaster = new CollectionRejectionAlertBroadcaster();
        CollectionRejectionRequestResponse duringGap = CollectionRejectionRequestResponse.builder()
                .id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();
        CollectionRejectionRequestResponse afterReconnect = CollectionRejectionRequestResponse.builder()
                .id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        broadcaster.stream().take(0).subscribe();
        broadcaster.publish(duringGap);

        StepVerifier.create(broadcaster.stream().take(2))
                .expectNext(duringGap)
                .then(() -> broadcaster.publish(afterReconnect))
                .expectNext(afterReconnect)
                .verifyComplete();
    }
}
