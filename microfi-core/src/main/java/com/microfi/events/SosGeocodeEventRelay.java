package com.microfi.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@link SosGeocodeEvent} from an in-JVM Spring application event (published inside
 * TrackingService's @Transactional raiseSos, before the surrounding transaction has committed) to
 * the actual RabbitMQ message (sent only {@link TransactionPhase#AFTER_COMMIT}) — mirrors
 * {@link CollectionGeocodeEventRelay} exactly, same ordering reasoning.
 */
@Component
@RequiredArgsConstructor
public class SosGeocodeEventRelay {

    private final SosGeocodePublisher sosGeocodePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSosRaised(SosGeocodeEvent event) {
        sosGeocodePublisher.publish(event.sosEventId(), event.lat(), event.lon());
    }
}
