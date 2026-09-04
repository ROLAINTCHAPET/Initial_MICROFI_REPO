package com.microfi.events;

import com.microfi.shared.dto.CollectionRejectionRequestResponse;
import com.microfi.transactions.service.CollectionRejectionAlertBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.Mockito.verify;

class CollectionRejectionAlertEventRelayTest {

    @Mock
    private CollectionRejectionAlertBroadcaster collectionRejectionAlertBroadcaster;

    private CollectionRejectionAlertEventRelay relay;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        relay = new CollectionRejectionAlertEventRelay(collectionRejectionAlertBroadcaster);
    }

    @Test
    void forwardsTheEventToTheBroadcaster() {
        CollectionRejectionRequestResponse response = CollectionRejectionRequestResponse.builder()
                .id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        relay.onCollectionRejectionRequested(new CollectionRejectionRequestedEvent(response));

        verify(collectionRejectionAlertBroadcaster).publish(response);
    }
}
