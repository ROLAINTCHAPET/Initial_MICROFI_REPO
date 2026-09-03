package com.microfi.events;

import com.microfi.transactions.service.SosAlertBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@link SosRaisedEvent} from an in-JVM Spring application event (published inside
 * TrackingService's @Transactional raiseSos, before the surrounding transaction has committed) to
 * the Back-Office SSE broadcaster — only {@link TransactionPhase#AFTER_COMMIT}, so a transaction
 * that rolls back never phantom-alerts an admin about an SOS that was never actually persisted.
 * Same ordering reasoning as {@link SosGeocodeEventRelay}, just relaying to an in-process
 * broadcaster instead of a cross-process RabbitMQ publish (see {@link SosAlertBroadcaster}'s doc).
 */
@Component
@RequiredArgsConstructor
public class SosAlertEventRelay {

    private final SosAlertBroadcaster sosAlertBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSosRaised(SosRaisedEvent event) {
        sosAlertBroadcaster.publish(event.response());
    }
}
