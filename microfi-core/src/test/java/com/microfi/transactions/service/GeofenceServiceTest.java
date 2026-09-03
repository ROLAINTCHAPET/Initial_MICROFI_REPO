package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.GeofenceRequest;
import com.microfi.shared.dto.GeofenceResponse;
import com.microfi.shared.dto.GeofenceVertexDto;
import com.microfi.transactions.domain.Geofence;
import com.microfi.transactions.domain.GeofenceAlert;
import com.microfi.transactions.repository.GeofenceAlertRepository;
import com.microfi.transactions.repository.GeofenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeofenceServiceTest {

    @Mock
    private GeofenceRepository geofenceRepository;
    @Mock
    private GeofenceAlertRepository geofenceAlertRepository;
    @Mock
    private AgentDirectoryService agentDirectoryService;

    private GeofenceService geofenceService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID geofenceId = UUID.randomUUID();

    // Square: lat 0..10, lon 0..10.
    private final Geofence squareGeofence = Geofence.builder().id(geofenceId).agentId(agentId)
            .verticesCsv("0,0;0,10;10,10;10,0").build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        geofenceService = new GeofenceService(geofenceRepository, geofenceAlertRepository, agentDirectoryService);
        ReflectionTestUtils.setField(geofenceService, "gracePeriodSeconds", 120L);
        when(geofenceAlertRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void evaluateLocationNoOpWhenAgentHasNoGeofence() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.empty());

        geofenceService.evaluateLocation(agentId, 5, 5);

        verify(geofenceAlertRepository, never()).save(any());
    }

    @Test
    void evaluateLocationInsidePolygonWithNoExistingBreachDoesNothing() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.empty());

        geofenceService.evaluateLocation(agentId, 5, 5);

        verify(geofenceAlertRepository, never()).save(any());
    }

    @Test
    void evaluateLocationOutsidePolygonCreatesUnraisedBreach() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.empty());

        geofenceService.evaluateLocation(agentId, 50, 50);

        ArgumentCaptor<GeofenceAlert> captor = ArgumentCaptor.forClass(GeofenceAlert.class);
        verify(geofenceAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getAgentId()).isEqualTo(agentId);
        assertThat(captor.getValue().getRaisedAt()).isNull();
        assertThat(captor.getValue().getFirstDetectedOutsideAt()).isNotNull();
    }

    @Test
    void evaluateLocationOutsideWithinGracePeriodDoesNotRaiseYet() {
        GeofenceAlert breach = GeofenceAlert.builder().id(UUID.randomUUID()).agentId(agentId).geofenceId(geofenceId)
                .firstDetectedOutsideAt(Instant.now().minusSeconds(10)).build();
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.of(breach));

        geofenceService.evaluateLocation(agentId, 50, 50);

        verify(geofenceAlertRepository, never()).save(any());
        assertThat(breach.getRaisedAt()).isNull();
    }

    @Test
    void evaluateLocationOutsidePastGracePeriodRaisesAlert() {
        GeofenceAlert breach = GeofenceAlert.builder().id(UUID.randomUUID()).agentId(agentId).geofenceId(geofenceId)
                .firstDetectedOutsideAt(Instant.now().minusSeconds(200)).build();
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.of(breach));

        geofenceService.evaluateLocation(agentId, 50, 50);

        verify(geofenceAlertRepository, times(1)).save(breach);
        assertThat(breach.getRaisedAt()).isNotNull();
    }

    @Test
    void evaluateLocationBackInsideAutoResolvesRaisedAlert() {
        GeofenceAlert breach = GeofenceAlert.builder().id(UUID.randomUUID()).agentId(agentId).geofenceId(geofenceId)
                .firstDetectedOutsideAt(Instant.now().minusSeconds(300)).raisedAt(Instant.now().minusSeconds(180)).build();
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.of(breach));

        geofenceService.evaluateLocation(agentId, 5, 5);

        verify(geofenceAlertRepository).save(breach);
        assertThat(breach.getResolvedAt()).isNotNull();
    }

    @Test
    void evaluateLocationBackInsideDiscardsBreachThatNeverClearedGracePeriod() {
        GeofenceAlert breach = GeofenceAlert.builder().id(UUID.randomUUID()).agentId(agentId).geofenceId(geofenceId)
                .firstDetectedOutsideAt(Instant.now().minusSeconds(10)).build();
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.of(breach));

        geofenceService.evaluateLocation(agentId, 5, 5);

        verify(geofenceAlertRepository).delete(breach);
        verify(geofenceAlertRepository, never()).save(any());
    }

    @Test
    void isWithinAssignedGeofenceTrueWhenNoGeofenceAssigned() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.empty());

        assertThat(geofenceService.isWithinAssignedGeofence(agentId, 500, 500)).isTrue();
    }

    @Test
    void isWithinAssignedGeofenceTrueWhenInsidePolygon() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));

        assertThat(geofenceService.isWithinAssignedGeofence(agentId, 5, 5)).isTrue();
    }

    @Test
    void isWithinAssignedGeofenceFalseWhenOutsidePolygon() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));

        assertThat(geofenceService.isWithinAssignedGeofence(agentId, 50, 50)).isFalse();
    }

    @Test
    void isWithinAssignedGeofenceNeverTouchesAlertState() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));

        geofenceService.isWithinAssignedGeofence(agentId, 50, 50);

        verify(geofenceAlertRepository, never()).save(any());
        verify(geofenceAlertRepository, never()).findByAgentIdAndResolvedAtIsNull(any());
    }

    @Test
    void setGeofenceCreatesAndGetGeofenceReturnsVertices() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.empty());
        when(geofenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GeofenceRequest request = new GeofenceRequest();
        GeofenceVertexDto v1 = new GeofenceVertexDto();
        v1.setLat(0.0);
        v1.setLon(0.0);
        GeofenceVertexDto v2 = new GeofenceVertexDto();
        v2.setLat(0.0);
        v2.setLon(10.0);
        GeofenceVertexDto v3 = new GeofenceVertexDto();
        v3.setLat(10.0);
        v3.setLon(10.0);
        request.setVertices(List.of(v1, v2, v3));

        GeofenceResponse response = geofenceService.setGeofence(agentId, request);

        assertThat(response.getAgentId()).isEqualTo(agentId);
        assertThat(response.getVertices()).hasSize(3);
    }

    @Test
    void applyGeofenceToBranchWritesSameVerticesToEveryActiveAgentAndReturnsCount() {
        UUID branchId = UUID.randomUUID();
        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agent1, agent2));
        when(geofenceRepository.findByAgentId(any())).thenReturn(Optional.empty());
        when(geofenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GeofenceRequest request = new GeofenceRequest();
        GeofenceVertexDto v1 = new GeofenceVertexDto();
        v1.setLat(0.0);
        v1.setLon(0.0);
        GeofenceVertexDto v2 = new GeofenceVertexDto();
        v2.setLat(0.0);
        v2.setLon(10.0);
        GeofenceVertexDto v3 = new GeofenceVertexDto();
        v3.setLat(10.0);
        v3.setLon(10.0);
        request.setVertices(List.of(v1, v2, v3));

        int count = geofenceService.applyGeofenceToBranch(branchId, request);

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<Geofence> captor = ArgumentCaptor.forClass(Geofence.class);
        verify(geofenceRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Geofence::getAgentId).containsExactlyInAnyOrder(agent1, agent2);
    }

    @Test
    void deleteGeofenceIsNoOpWhenAgentHasNone() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.empty());

        geofenceService.deleteGeofence(agentId);

        verify(geofenceRepository, never()).delete(any());
        verify(geofenceAlertRepository, never()).findByAgentIdAndResolvedAtIsNull(any());
    }

    @Test
    void deleteGeofenceRemovesGeofenceWithNoOpenBreach() {
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.empty());

        geofenceService.deleteGeofence(agentId);

        verify(geofenceRepository).delete(squareGeofence);
        verify(geofenceAlertRepository, never()).save(any());
        verify(geofenceAlertRepository, never()).delete(any());
    }

    @Test
    void deleteGeofenceResolvesOpenRaisedBreachBeforeDeleting() {
        GeofenceAlert breach = GeofenceAlert.builder().id(UUID.randomUUID()).agentId(agentId).geofenceId(geofenceId)
                .firstDetectedOutsideAt(Instant.now().minusSeconds(300)).raisedAt(Instant.now().minusSeconds(180)).build();
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.of(breach));

        geofenceService.deleteGeofence(agentId);

        verify(geofenceAlertRepository).save(breach);
        assertThat(breach.getResolvedAt()).isNotNull();
        verify(geofenceRepository).delete(squareGeofence);
    }

    @Test
    void deleteGeofenceDiscardsOpenUnraisedBreachBeforeDeleting() {
        GeofenceAlert breach = GeofenceAlert.builder().id(UUID.randomUUID()).agentId(agentId).geofenceId(geofenceId)
                .firstDetectedOutsideAt(Instant.now().minusSeconds(10)).build();
        when(geofenceRepository.findByAgentId(agentId)).thenReturn(Optional.of(squareGeofence));
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(Optional.of(breach));

        geofenceService.deleteGeofence(agentId);

        verify(geofenceAlertRepository).delete(breach);
        verify(geofenceAlertRepository, never()).save(any());
        verify(geofenceRepository).delete(squareGeofence);
    }

    @Test
    void clearGeofenceFromBranchDeletesOnlyAgentsThatHaveOneAndReturnsCount() {
        UUID branchId = UUID.randomUUID();
        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agent1, agent2));
        Geofence agent1Geofence = Geofence.builder().id(UUID.randomUUID()).agentId(agent1).verticesCsv("0,0;0,10;10,10").build();
        when(geofenceRepository.findByAgentId(agent1)).thenReturn(Optional.of(agent1Geofence));
        when(geofenceRepository.findByAgentId(agent2)).thenReturn(Optional.empty());
        when(geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agent1)).thenReturn(Optional.empty());

        int cleared = geofenceService.clearGeofenceFromBranch(branchId);

        assertThat(cleared).isEqualTo(1);
        verify(geofenceRepository).delete(agent1Geofence);
        verify(geofenceRepository, never()).delete(argThat(g -> g.getAgentId().equals(agent2)));
    }
}
