package com.microfi.audit.domain;

/**
 * Mirrors the compliance audit export's pillars. SECURITY and COMPLIANCE have no table of their
 * own and live entirely in audit_log. FINANCIAL is a lightweight pointer only (e.g.
 * COLLECTION_RECORDED) — the full transaction detail still lives in Collection/EscrowLedger/
 * ActivationPayment, exported from there; this row exists so the event still shows up in the
 * unified /audit timeline alongside security/compliance events.
 */
public enum AuditCategory {
    SECURITY,
    COMPLIANCE,
    FINANCIAL
}
