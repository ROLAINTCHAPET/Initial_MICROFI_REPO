package com.microfi.events;

import com.microfi.shared.dto.OfjClosingExportAlertResponse;

/** Carries a just-completed scheduled closing-time OFJ export to {@link OfjClosingExportAlertEventRelay} — mirrors {@link CollectionRejectionRequestedEvent}'s placement/timing exactly. */
public record OfjClosingExportCompletedEvent(
    OfjClosingExportAlertResponse response
) {
}
