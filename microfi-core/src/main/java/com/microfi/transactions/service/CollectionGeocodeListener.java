package com.microfi.transactions.service;

import com.microfi.config.RabbitMQConfig;
import com.microfi.events.CollectionGeocodeEvent;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.repository.CollectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The async half of {@link CollectionService}'s reverse-geocoding: consumes what
 * {@link com.microfi.events.CollectionGeocodePublisher} publishes right after a collection is
 * saved, resolves the position via the same {@link GeocodingService} that used to be called
 * inline, and fills in {@code Collection#locationName} after the fact.
 * <p>
 * Best-effort by construction, same as the synchronous call it replaces: GeocodingService itself
 * already swallows lookup failures/timeouts and returns null rather than throwing, so a bad
 * lookup here just leaves locationName null — it never requeues or dead-letters the message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionGeocodeListener {

    private final CollectionRepository collectionRepository;
    private final GeocodingService geocodingService;

    @RabbitListener(queues = RabbitMQConfig.COLLECTION_GEOCODE_QUEUE)
    @Transactional
    public void onGeocodeEvent(CollectionGeocodeEvent event) {
        Collection collection = collectionRepository.findById(event.collectionId()).orElse(null);
        if (collection == null) {
            // Nothing left to update — not expected in practice, but not worth dead-lettering over.
            log.warn("Geocode event for unknown collection {}", event.collectionId());
            return;
        }

        String locationName = geocodingService.reverseGeocode(event.lat(), event.lon());
        if (locationName == null) {
            return;
        }
        collection.setLocationName(locationName);
        collectionRepository.save(collection);
    }
}
