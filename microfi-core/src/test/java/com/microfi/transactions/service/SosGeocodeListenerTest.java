package com.microfi.transactions.service;

import com.microfi.events.SosGeocodeEvent;
import com.microfi.transactions.domain.SosEvent;
import com.microfi.transactions.repository.SosEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SosGeocodeListenerTest {

    @Mock
    private SosEventRepository sosEventRepository;
    @Mock
    private GeocodingService geocodingService;

    private SosGeocodeListener listener;

    private final UUID sosEventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new SosGeocodeListener(sosEventRepository, geocodingService);
    }

    private SosEvent existingSosEvent() {
        return SosEvent.builder().id(sosEventId).agentId(UUID.randomUUID()).lat(4.05).lon(9.70).raisedAt(Instant.now()).build();
    }

    @Test
    void resolvesAndSavesTheLocationName() {
        SosEvent event = existingSosEvent();
        when(sosEventRepository.findById(sosEventId)).thenReturn(Optional.of(event));
        when(geocodingService.reverseGeocode(4.05, 9.70)).thenReturn("Akwa, Douala, Cameroon");

        listener.onGeocodeEvent(new SosGeocodeEvent(sosEventId, 4.05, 9.70));

        assertThat(event.getLocationName()).isEqualTo("Akwa, Douala, Cameroon");
        verify(sosEventRepository).save(event);
    }

    @Test
    void leavesLocationNameNullWhenGeocodingFails() {
        SosEvent event = existingSosEvent();
        when(sosEventRepository.findById(sosEventId)).thenReturn(Optional.of(event));
        when(geocodingService.reverseGeocode(4.05, 9.70)).thenReturn(null);

        listener.onGeocodeEvent(new SosGeocodeEvent(sosEventId, 4.05, 9.70));

        assertThat(event.getLocationName()).isNull();
        verify(sosEventRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenTheSosEventNoLongerExists() {
        when(sosEventRepository.findById(sosEventId)).thenReturn(Optional.empty());

        listener.onGeocodeEvent(new SosGeocodeEvent(sosEventId, 4.05, 9.70));

        verify(geocodingService, never()).reverseGeocode(anyDouble(), anyDouble());
        verify(sosEventRepository, never()).save(any());
    }
}
