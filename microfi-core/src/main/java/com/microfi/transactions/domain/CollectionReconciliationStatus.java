package com.microfi.transactions.domain;

/**
 * Gates when a collection actually relieves the agent's escrow ceiling — distinct from
 * {@link Collection#getReconciledInLineId()}, which is stamped immediately at cashier-submit time
 * regardless of this status so CBS export/branch-closing stay unaffected (see OfjService's class
 * doc). {@code UNRECONCILED} is the default, pre-cashier-count state; a cashier's physical count
 * moves a collection straight to {@code PENDING_AGENT_CONFIRMATION} (see OfjService#reconcile);
 * only {@code CONFIRMED} (agent tap or auto-expiry) sets {@link Collection#getReconciledAt()} and
 * frees the ceiling.
 */
public enum CollectionReconciliationStatus {
    UNRECONCILED,
    PENDING_AGENT_CONFIRMATION,
    CONFIRMED
}
