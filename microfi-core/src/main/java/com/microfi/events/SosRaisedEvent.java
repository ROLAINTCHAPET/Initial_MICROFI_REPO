package com.microfi.events;

import com.microfi.shared.dto.SosResponse;

/** UC-14: carries the just-raised SOS to {@link SosAlertEventRelay} — mirrors {@link SosGeocodeEvent}'s placement/timing (published inside TrackingService's @Transactional raiseSos), but carries the full response rather than just an id since the relay broadcasts it as-is, with nothing left to re-look-up. */
public record SosRaisedEvent(
    SosResponse response
) {
}
