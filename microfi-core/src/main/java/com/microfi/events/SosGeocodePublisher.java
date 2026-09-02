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
public class SosGeocodePublisher {

    private final RabbitTemplate rabbitTemplate;

    // Reverse-geocoding is a display nicety, not part of raising the alert itself — a broker
    // outage must never fail (or even delay) the SOS trigger, same reasoning as
    // CollectionGeocodePublisher. Worst case, this event's locationName just stays null.
    public void publish(UUID sosEventId, double lat, double lon) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.COLLECTION_EXCHANGE, RabbitMQConfig.SOS_GEOCODE_KEY,
                    new SosGeocodeEvent(sosEventId, lat, lon));
        } catch (Exception e) {
            log.warn("Failed to publish geocode event for SOS event {}: {}", sosEventId, e.getMessage());
        }
    }
}
