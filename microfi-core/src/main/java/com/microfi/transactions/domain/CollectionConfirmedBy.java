package com.microfi.transactions.domain;

/** How a collection reached {@link CollectionReconciliationStatus#CONFIRMED} — kept for the audit trail, not for any behavioral branching. */
public enum CollectionConfirmedBy {
    AGENT,
    SYSTEM_AUTO_EXPIRY
}
