package com.microfi.authentication.domain;

public enum SecurityEventType {
    /** Same device (imei), different installation id — the uninstall/reinstall fraud scenario this whole feature exists to catch. */
    INSTALLATION_MISMATCH,
    /** A different physical device entirely — AuthenticationController already hard-rejects the login itself for this one; the event is raised for the admin's forensic trail. */
    DEVICE_MISMATCH,
    /** A synced collection's counter is ahead of the expected next value — held for review, not treated as automatically malicious (unlike a bad hash/signature, a gap can also mean lost/undelivered records). */
    COUNTER_GAP,
    /** A synced collection's previousHash doesn't match the prior record's currentHash — chain continuity broken, an unambiguous forgery/tamper signal. */
    BAD_PREVIOUS_HASH,
    /** A synced collection's signature doesn't verify against the installation's HMAC secret. */
    BAD_SIGNATURE,
    /** A synced collection presented an installationId that isn't this agent's current authorized binding. */
    DEVICE_NOT_AUTHORIZED
}
