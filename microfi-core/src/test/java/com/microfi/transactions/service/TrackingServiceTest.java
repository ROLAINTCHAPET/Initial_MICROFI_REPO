package com.microfi.transactions.service;

import com.microfi.shared.dto.LocationPingRequest;
import com.microfi.shared.dto.LocationPingResponse;
import com.microfi.shared.dto.SosRequest;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.repository.LocationPingRepository;
import com.microfi.transactions.repository.SosEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TrackingServiceTest {

    @Mock
    private LocationPingRepository locationPingRepository;
    @Mock
    private SosEventRepository sosEventRepository;

    private TrackingService trackingService;

    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        trackingService = new TrackingService(locationPingRepository, sosEventRepository);
        when(locationPingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sosEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void recordLocationPersistsAndReturnsPing() {
        LocationPingRequest request = new LocationPingRequest();
        request.setLat(4.05);
        request.setLon(9.70);

        LocationPingResponse response = trackingService.recordLocation(agentId, request);

        assertThat(response.getAgentId()).isEqualTo(agentId);
        assertThat(response.getLat()).isEqualTo(4.05);
        assertThat(response.getLon()).isEqualTo(9.70);
        assertThat(response.getRecordedAt()).isNotNull();
    }

    @Test
    void raiseSosPersistsAndReturnsEventWithGps() {
        SosRequest request = new SosRequest();
        request.setLat(4.05);
        request.setLon(9.70);

        SosResponse response = trackingService.raiseSos(agentId, request);

        assertThat(response.getAgentId()).isEqualTo(agentId);
        assertThat(response.getLat()).isEqualTo(4.05);
        assertThat(response.getRaisedAt()).isNotNull();
    }

    @Test
    void raiseSosAcceptedWithoutGpsFix() {
        SosRequest request = new SosRequest();

        SosResponse response = trackingService.raiseSos(agentId, request);

        assertThat(response.getAgentId()).isEqualTo(agentId);
        assertThat(response.getLat()).isNull();
        assertThat(response.getLon()).isNull();
        assertThat(response.getRaisedAt()).isNotNull();
    }
}
