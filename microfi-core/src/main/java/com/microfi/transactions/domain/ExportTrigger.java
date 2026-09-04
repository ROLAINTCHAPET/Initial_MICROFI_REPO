package com.microfi.transactions.domain;

/**
 * Why a given {@link ExportBatch} run happened — now that export is repeatable per session
 * (multiple runs a day are legal, each idempotent on already-exported cash), this is what lets an
 * audit trail/reports screen tell a back-office-initiated export apart from the automatic ones.
 */
public enum ExportTrigger {
    MANUAL,
    AUTO_ON_CLOSE,
    SCHEDULED_CLOSING_TIME
}
