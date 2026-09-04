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

    // Regression: onBackpressureBuffer()'s plain overload defaults to autoCancel=true, which
    // permanently terminates the sink once its subscriber count drops to zero — exactly what
    // happens between two admins' Back-Office tabs both being closed. A later subscriber must
    // still receive alerts published after it connects, even though nobody was listening for a
    // while in between.
    @Test
    void survivesDroppingToZeroSubscribersBeforeALaterOneConnects() {
        SosAlertBroadcaster broadcaster = new SosAlertBroadcaster();
        SosResponse duringGap = SosResponse.builder().id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();
        SosResponse afterReconnect = SosResponse.builder().id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        broadcaster.stream().take(0).subscribe();
        broadcaster.publish(duringGap);

        // The buffer replays duringGap (published with zero active subscribers) to the next
        // subscriber first — that's expected of a buffering sink. What this regression test
        // actually guards is that the sink is still alive at all: afterReconnect, published only
        // once this second subscriber is already connected, must still arrive right behind it
        // instead of the sink having silently terminated from the earlier zero-subscriber gap.
        StepVerifier.create(broadcaster.stream().take(2))
                .expectNext(duringGap)
                .then(() -> broadcaster.publish(afterReconnect))
                .expectNext(afterReconnect)
                .verifyComplete();
    }
}
