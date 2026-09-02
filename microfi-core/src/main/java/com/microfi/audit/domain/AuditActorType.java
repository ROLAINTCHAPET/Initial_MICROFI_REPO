package com.microfi.audit.domain;

/** Which kind of principal performed the audited action — every actor type in the system, not just Back-Office admins. */
public enum AuditActorType {
    ADMIN,
    AGENT,
    CLIENT,
    SYSTEM
}
