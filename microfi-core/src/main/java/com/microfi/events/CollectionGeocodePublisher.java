package com.microfi.events;

import com.microfi.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionGeocodePublisher {

    private final RabbitTemplate rabbitTemplate;

    // Reverse-geocoding is a display nicety, not part of recording the collection — a broker
    // outage must never fail (or even delay) the collection itself, same reasoning as
    // AuthEventPublisher. Worst case, this collection's locationName just stays null, exactly as
    // it already does today whenever Nominatim times out or errors.
    public void publish(UUID collectionId, double lat, double lon) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.COLLECTION_EXCHANGE, RabbitMQConfig.COLLECTION_GEOCODE_KEY,
                    new CollectionGeocodeEvent(collectionId, lat, lon));
        } catch (Exception e) {
            log.warn("Failed to publish geocode event for collection {}: {}", collectionId, e.getMessage());
        }
    }
}
