package com.microfi.events;

import java.util.UUID;

/** UC-06/07/08: the payload behind async reverse-geocoding — just enough to look the collection back up and re-resolve its position, nothing that duplicates data the Collection row already owns. */
public record CollectionGeocodeEvent(
    UUID collectionId,
    double lat,
    double lon
) {
}
