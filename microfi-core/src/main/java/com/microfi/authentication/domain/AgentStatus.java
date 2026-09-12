package com.microfi.authentication.domain;

public enum AgentStatus {
    /** Enrolled but not yet usable — the escrow account has no ceiling until an admin funds it via a top-up (EscrowService#topUp). */
    PENDING_CEILING,
    ACTIVE,
    SUSPENDED,
    /** A login presented an installation/device that doesn't match this agent's current binding (see SecurityEventService#raise) — collection is blocked (AgentDirectoryService#verifyTransactionPin) until an admin resolves it via AgentManagementController#resetDeviceBinding. Login itself is NOT blocked — the agent needs to be able to see why they're locked out, and the login attempt is itself forensic signal. */
    RECONCILIATION_REQUIRED,
    /** An admin has reset the agent's device/installation binding. May attempt collections, but an OFFLINE one is held until the newly (re)bound installation completes one ONLINE collection — see AgentDirectoryService#requireOnlineFirstCollectionCompletedForOffline. Flips back to ACTIVE automatically once that happens. */
    RESET_AUTHORIZED,
    DELETED
}
