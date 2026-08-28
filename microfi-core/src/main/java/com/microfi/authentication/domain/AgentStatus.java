package com.microfi.authentication.domain;

public enum AgentStatus {
    /** Enrolled but not yet usable — the escrow account has no ceiling until an admin funds it via a top-up (EscrowService#topUp). */
    PENDING_CEILING,
    ACTIVE,
    SUSPENDED,
    DELETED
}
