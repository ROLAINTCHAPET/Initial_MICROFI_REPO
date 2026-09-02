package com.microfi.transactions.domain;

public enum VarianceDebtStatus {
    OPEN,
    RESOLVED,
    /** ADMIN-only write-off (VarianceDebtController#writeOff) — distinct from RESOLVED, which would mean the agent actually repaid the shortage. */
    WRITTEN_OFF
}
