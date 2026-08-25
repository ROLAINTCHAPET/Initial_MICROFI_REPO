package com.microfi.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.Mockito.verify;

class CollectionGeocodeEventRelayTest {

    @Mock
    private CollectionGeocodePublisher collectionGeocodePublisher;

    private CollectionGeocodeEventRelay relay;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        relay = new CollectionGeocodeEventRelay(collectionGeocodePublisher);
    }

    @Test
    void forwardsTheEventToTheRabbitMqPublisher() {
        UUID collectionId = UUID.randomUUID();

        relay.onCollectionSaved(new CollectionGeocodeEvent(collectionId, 4.05, 9.70));

        verify(collectionGeocodePublisher).publish(collectionId, 4.05, 9.70);
    }
}
