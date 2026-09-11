package com.microfi.events;

import com.microfi.transactions.service.AgentMisconductReportAlertBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges {@link AgentMisconductReportedEvent} to the Back-Office SSE broadcaster — only
 * {@link TransactionPhase#AFTER_COMMIT}, so a transaction that rolls back never phantom-notifies
 * an admin about a report that was never actually persisted. Mirrors {@link SosAlertEventRelay}
 * exactly.
 */
@Component
@RequiredArgsConstructor
public class AgentMisconductReportAlertEventRelay {

    private final AgentMisconductReportAlertBroadcaster agentMisconductReportAlertBroadcaster;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgentMisconductReported(AgentMisconductReportedEvent event) {
        agentMisconductReportAlertBroadcaster.publish(event.response());
    }
}
