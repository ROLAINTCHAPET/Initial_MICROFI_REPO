package com.microfi.transactions.service;

import com.microfi.shared.dto.SosResponse;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.UUID;

class SosAlertBroadcasterTest {

    @Test
    void publishedEventReachesAnAlreadySubscribedStream() {
        SosAlertBroadcaster broadcaster = new SosAlertBroadcaster();
        SosResponse event = SosResponse.builder().id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        StepVerifier.create(broadcaster.stream().take(1))
                .then(() -> broadcaster.publish(event))
                .expectNext(event)
                .verifyComplete();
    }

    @Test
    void eachSubscriberReceivesEventsPublishedAfterItSubscribed() {
        SosAlertBroadcaster broadcaster = new SosAlertBroadcaster();
        SosResponse first = SosResponse.builder().id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();
        SosResponse second = SosResponse.builder().id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        StepVerifier.create(broadcaster.stream().take(2))
                .then(() -> broadcaster.publish(first))
                .expectNext(first)
                .then(() -> broadcaster.publish(second))
                .expectNext(second)
                .verifyComplete();
    }
}
