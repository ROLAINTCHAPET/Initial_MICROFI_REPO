package com.microfi.events;

import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.service.SosAlertBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.mockito.Mockito.verify;

class SosAlertEventRelayTest {

    @Mock
    private SosAlertBroadcaster sosAlertBroadcaster;

    private SosAlertEventRelay relay;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        relay = new SosAlertEventRelay(sosAlertBroadcaster);
    }

    @Test
    void forwardsTheEventToTheBroadcaster() {
        SosResponse response = SosResponse.builder().id(UUID.randomUUID()).agentId(UUID.randomUUID()).build();

        relay.onSosRaised(new SosRaisedEvent(response));

        verify(sosAlertBroadcaster).publish(response);
    }
}
