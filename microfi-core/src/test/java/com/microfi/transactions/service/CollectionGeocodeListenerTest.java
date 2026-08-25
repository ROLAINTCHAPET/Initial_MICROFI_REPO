package com.microfi.transactions.service;

import com.microfi.events.CollectionGeocodeEvent;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.repository.CollectionRepository;
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

    private Collection existingCollection() {
        return Collection.builder().id(collectionId).agentId(UUID.randomUUID()).clientId(UUID.randomUUID())
                .amountXaf(5000).lat(4.05).lon(9.70).collectedAt(Instant.now()).deviceTxId("DEV-TX-1").build();
    }

    @Test
    void resolvesAndSavesTheLocationName() {
        Collection collection = existingCollection();
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(geocodingService.reverseGeocode(4.05, 9.70)).thenReturn("Akwa, Douala, Cameroon");

        listener.onGeocodeEvent(new CollectionGeocodeEvent(collectionId, 4.05, 9.70));

        assertThat(collection.getLocationName()).isEqualTo("Akwa, Douala, Cameroon");
        verify(collectionRepository).save(collection);
    }

    @Test
    void leavesLocationNameNullWhenGeocodingFails() {
        Collection collection = existingCollection();
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection));
        when(geocodingService.reverseGeocode(4.05, 9.70)).thenReturn(null);

        listener.onGeocodeEvent(new CollectionGeocodeEvent(collectionId, 4.05, 9.70));

        assertThat(collection.getLocationName()).isNull();
        verify(collectionRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenTheCollectionNoLongerExists() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.empty());

        listener.onGeocodeEvent(new CollectionGeocodeEvent(collectionId, 4.05, 9.70));

        verify(geocodingService, never()).reverseGeocode(anyDouble(), anyDouble());
        verify(collectionRepository, never()).save(any());
    }
}
