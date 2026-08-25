package com.microfi.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@link CollectionGeocodeEvent} from an in-JVM Spring application event (published
 * inside CollectionService's @Transactional method, before the surrounding transaction has
 * committed) to the actual RabbitMQ message (sent only {@link TransactionPhase#AFTER_COMMIT}).
 * <p>
 * Without this indirection, publishing straight to RabbitMQ from inside the transaction lets a
 * fast consumer race the commit — it can look up the collection before the save is actually
 * visible in the database. This relay is what makes the ordering safe: by the time
 * {@link CollectionGeocodePublisher#publish} runs, the row is guaranteed to exist.
 */
@Component
@RequiredArgsConstructor
public class CollectionGeocodeEventRelay {

    private final CollectionGeocodePublisher collectionGeocodePublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollectionSaved(CollectionGeocodeEvent event) {
        collectionGeocodePublisher.publish(event.collectionId(), event.lat(), event.lon());
    }
}
