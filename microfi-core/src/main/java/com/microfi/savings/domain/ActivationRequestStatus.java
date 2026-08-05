package com.microfi.savings.domain;

/** Two-party activation gate state — see {@link ActivationRequest}. */
public enum ActivationRequestStatus {
    PENDING,
    COMPLETED,
    /** Manually voided by an admin/branch manager — see {@code cancelledBy}/{@code cancelReason}. */
    CANCELLED,
    /** Auto-voided by {@code ActivationRequestExpiryJob} after sitting PENDING too long. */
    EXPIRED
}
