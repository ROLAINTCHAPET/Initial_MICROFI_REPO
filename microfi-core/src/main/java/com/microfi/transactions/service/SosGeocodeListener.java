package com.microfi.transactions.service;

import com.microfi.config.RabbitMQConfig;
import com.microfi.events.SosGeocodeEvent;
import com.microfi.transactions.domain.SosEvent;
import com.microfi.transactions.repository.SosEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The async half of {@link TrackingService#raiseSos}'s reverse-geocoding: consumes what
 * {@link com.microfi.events.SosGeocodePublisher} publishes right after an SOS event is saved,
 * resolves the position via the same {@link GeocodingService} collections already use, and fills
 * in {@code SosEvent#locationName} after the fact — mirrors {@link CollectionGeocodeListener}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SosGeocodeListener {

    private final SosEventRepository sosEventRepository;
    private final GeocodingService geocodingService;

    @RabbitListener(queues = RabbitMQConfig.SOS_GEOCODE_QUEUE)
    @Transactional
    public void onGeocodeEvent(SosGeocodeEvent event) {
        SosEvent sosEvent = sosEventRepository.findById(event.sosEventId()).orElse(null);
        if (sosEvent == null) {
            log.warn("Geocode event for unknown SOS event {}", event.sosEventId());
            return;
        }

        String locationName = geocodingService.reverseGeocode(event.lat(), event.lon());
        if (locationName == null) {
            return;
        }
        sosEvent.setLocationName(locationName);
        sosEventRepository.save(sosEvent);
    }
}
