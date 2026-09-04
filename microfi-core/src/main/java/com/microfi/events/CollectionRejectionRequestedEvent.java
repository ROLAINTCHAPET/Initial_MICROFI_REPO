package com.microfi.events;

import com.microfi.shared.dto.CollectionRejectionRequestResponse;

/** Carries a just-submitted collection-rejection request to {@link CollectionRejectionAlertEventRelay} — mirrors {@link SosRaisedEvent}'s placement/timing exactly. */
public record CollectionRejectionRequestedEvent(
    CollectionRejectionRequestResponse response
) {
}
