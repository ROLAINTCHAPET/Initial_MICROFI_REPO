package com.microfi.transactions.service;

import com.microfi.shared.dto.LocationPingRequest;
import com.microfi.shared.dto.LocationPingResponse;
import com.microfi.shared.dto.SosRequest;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.domain.SosEvent;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.LocationPingRepository;
import com.microfi.transactions.repository.SosEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingServiceTest {

    @Mock
    private LocationPingRepository locationPingRepository;
    @Mock
    private SosEventRepository sosEventRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private GeofenceService geofenceService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private TrackingService trackingService;

    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        trackingService = new TrackingService(locationPingRepository, sosEventRepository, collectionRepository, geofenceService, applicationEventPublisher);
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
        verify(geofenceService).evaluateLocation(agentId, 4.05, 9.70);
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
        verify(applicationEventPublisher).publishEvent(any(com.microfi.events.SosGeocodeEvent.class));
    }

    @Test
    void raiseSosAcceptedWithoutGpsFix() {
        SosRequest request = new SosRequest();

        SosResponse response = trackingService.raiseSos(agentId, request);

        assertThat(response.getAgentId()).isEqualTo(agentId);
        assertThat(response.getLat()).isNull();
        assertThat(response.getLon()).isNull();
        assertThat(response.getRaisedAt()).isNotNull();
        // Nothing to geocode without a fix — must not publish an event with a null lat/lon.
        verify(applicationEventPublisher, org.mockito.Mockito.never()).publishEvent(any());
    }

    @Test
    void getRouteReturnsOrderedPointsAndTransactionMarkers() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        com.microfi.transactions.domain.LocationPing ping = com.microfi.transactions.domain.LocationPing.builder()
                .id(UUID.randomUUID()).agentId(agentId).lat(4.05).lon(9.70).recordedAt(java.time.Instant.now()).build();
        com.microfi.transactions.domain.Collection collection = com.microfi.transactions.domain.Collection.builder()
                .id(UUID.randomUUID()).agentId(agentId).clientId(UUID.randomUUID())
                .amountXaf(5000L).lat(4.06).lon(9.71).locationName("Akwa, Douala, Cameroon").collectedAt(java.time.Instant.now()).build();
        when(locationPingRepository.findByAgentIdAndRecordedAtBetweenOrderByRecordedAtAsc(any(), any(), any()))
                .thenReturn(java.util.List.of(ping));
        when(collectionRepository.findByAgentIdInAndCollectedAtBetween(any(), any(), any()))
                .thenReturn(java.util.List.of(collection));

        var route = trackingService.getRoute(agentId, today);

        assertThat(route.getAgentId()).isEqualTo(agentId);
        assertThat(route.getPoints()).hasSize(1);
        assertThat(route.getPoints().get(0).getLat()).isEqualTo(4.05);
        assertThat(route.getTransactions()).hasSize(1);
        assertThat(route.getTransactions().get(0).getAmountXaf()).isEqualTo(5000L);
        assertThat(route.getTransactions().get(0).getLocationName()).isEqualTo("Akwa, Douala, Cameroon");
    }

    @Test
    void listSosEventsUnrestrictedWhenAgentIdsNull() {
        SosEvent event = SosEvent.builder().id(UUID.randomUUID()).agentId(agentId).raisedAt(Instant.now())
                .locationName("Douala, Cameroon").build();
        when(sosEventRepository.findAllByOrderByRaisedAtDesc()).thenReturn(List.of(event));

        List<SosResponse> result = trackingService.listSosEvents(null, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAgentId()).isEqualTo(agentId);
        assertThat(result.get(0).getLocationName()).isEqualTo("Douala, Cameroon");
    }

    @Test
    void listSosEventsScopedToAgentIdsAndUnresolvedOnly() {
        SosEvent event = SosEvent.builder().id(UUID.randomUUID()).agentId(agentId).raisedAt(Instant.now()).build();
        when(sosEventRepository.findByAgentIdInAndAcknowledgedAtIsNullOrderByRaisedAtDesc(List.of(agentId))).thenReturn(List.of(event));

        List<SosResponse> result = trackingService.listSosEvents(List.of(agentId), true);

        assertThat(result).hasSize(1);
    }

    @Test
    void acknowledgeSosSetsAcknowledgedByAndAt() {
        UUID sosEventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SosEvent event = SosEvent.builder().id(sosEventId).agentId(agentId).raisedAt(Instant.now()).build();
        when(sosEventRepository.findById(sosEventId)).thenReturn(Optional.of(event));
        when(sosEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SosResponse response = trackingService.acknowledgeSos(sosEventId, adminId);

        assertThat(response.getAcknowledgedBy()).isEqualTo(adminId);
        assertThat(response.getAcknowledgedAt()).isNotNull();
    }

    @Test
    void acknowledgeSosAlreadyAcknowledgedThrowsConflict() {
        UUID sosEventId = UUID.randomUUID();
        SosEvent event = SosEvent.builder().id(sosEventId).agentId(agentId).raisedAt(Instant.now())
                .acknowledgedBy(UUID.randomUUID()).acknowledgedAt(Instant.now()).build();
        when(sosEventRepository.findById(sosEventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> trackingService.acknowledgeSos(sosEventId, UUID.randomUUID()))
                .hasMessageContaining("409");
    }

    @Test
    void findSosEventAgentIdNotFoundThrows404() {
        UUID sosEventId = UUID.randomUUID();
        when(sosEventRepository.findById(sosEventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trackingService.findSosEventAgentId(sosEventId))
                .hasMessageContaining("404");
    }
}
