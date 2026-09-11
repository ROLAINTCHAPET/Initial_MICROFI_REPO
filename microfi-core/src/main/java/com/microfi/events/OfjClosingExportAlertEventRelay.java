package com.microfi.events;

import com.microfi.transactions.service.OfjClosingExportAlertBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@link OfjClosingExportCompletedEvent} to the Back-Office SSE broadcaster — only
 * {@link TransactionPhase#AFTER_COMMIT}, so a transaction that rolls back never phantom-notifies
 * anyone about an export that was never actually persisted. Mirrors
 * {@link CollectionRejectionAlertEventRelay} exactly.
 */
@Component
@RequiredArgsConstructor
public class OfjClosingExportAlertEventRelay {

    private final OfjClosingExportAlertBroadcaster ofjClosingExportAlertBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOfjClosingExportCompleted(OfjClosingExportCompletedEvent event) {
        ofjClosingExportAlertBroadcaster.publish(event.response());
    }
}
