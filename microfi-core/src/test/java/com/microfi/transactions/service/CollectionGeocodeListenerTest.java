package com.microfi.transactions.service;

import com.microfi.events.CollectionGeocodeEvent;
import com.microfi.transactions.repository.CollectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionGeocodeListenerTest {

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private GeocodingService geocodingService;

    private CollectionGeocodeListener listener;

    private final UUID collectionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new CollectionGeocodeListener(collectionRepository, geocodingService);
    }

    @Test
    void resolvesAndUpdatesOnlyTheLocationNameColumn() {
        when(collectionRepository.existsById(collectionId)).thenReturn(true);
        when(geocodingService.reverseGeocode(4.05, 9.70)).thenReturn("Akwa, Douala, Cameroon");

        listener.onGeocodeEvent(new CollectionGeocodeEvent(collectionId, 4.05, 9.70));

        verify(collectionRepository).updateLocationName(collectionId, "Akwa, Douala, Cameroon");
    }

    @Test
    void leavesLocationNameUntouchedWhenGeocodingFails() {
        when(collectionRepository.existsById(collectionId)).thenReturn(true);
        when(geocodingService.reverseGeocode(4.05, 9.70)).thenReturn(null);

        listener.onGeocodeEvent(new CollectionGeocodeEvent(collectionId, 4.05, 9.70));

        verify(collectionRepository, never()).updateLocationName(any(), anyString());
    }

    @Test
    void doesNothingWhenTheCollectionNoLongerExists() {
        when(collectionRepository.existsById(collectionId)).thenReturn(false);

        listener.onGeocodeEvent(new CollectionGeocodeEvent(collectionId, 4.05, 9.70));

        verify(geocodingService, never()).reverseGeocode(anyDouble(), anyDouble());
        verify(collectionRepository, never()).updateLocationName(any(), anyString());
    }
}
