package com.microfi.events;

import java.util.UUID;

/** UC-14: the payload behind async reverse-geocoding for an SOS alert — mirrors {@link CollectionGeocodeEvent}, just enough to look the event back up and re-resolve its position. */
public record SosGeocodeEvent(
    UUID sosEventId,
    double lat,
    double lon
) {
}
