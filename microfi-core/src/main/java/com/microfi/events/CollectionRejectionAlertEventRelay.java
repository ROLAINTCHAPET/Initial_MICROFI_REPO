package com.microfi.events;

import com.microfi.transactions.service.CollectionRejectionAlertBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@link CollectionRejectionRequestedEvent} to the Back-Office SSE broadcaster — only
 * {@link TransactionPhase#AFTER_COMMIT}, so a transaction that rolls back never phantom-notifies
 * an admin about a request that was never actually persisted. Mirrors {@link SosAlertEventRelay}
 * exactly.
 */
@Component
@RequiredArgsConstructor
public class CollectionRejectionAlertEventRelay {

    private final CollectionRejectionAlertBroadcaster collectionRejectionAlertBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCollectionRejectionRequested(CollectionRejectionRequestedEvent event) {
        collectionRejectionAlertBroadcaster.publish(event.response());
    }
}
