package com.microfi.transactions.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.events.CollectionGeocodeEvent;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.CollectionRequest;
import com.microfi.shared.dto.CollectionResponse;
import com.microfi.shared.dto.DenominationLineDto;
import com.microfi.shared.dto.EscrowResponse;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.DenominationLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionServiceTest {

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private DenominationLineRepository denominationLineRepository;
    @Mock
    private ClientDirectoryService clientDirectoryService;
    @Mock
    private EscrowService escrowService;
    @Mock
    private ActivationDirectoryService activationDirectoryService;
    @Mock
    private AgentDirectoryService agentDirectoryService;
    @Mock
    private GeofenceService geofenceService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private AuditService auditService;

    private CollectionService collectionService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        collectionService = new CollectionService(collectionRepository, denominationLineRepository, clientDirectoryService, escrowService, activationDirectoryService, agentDirectoryService, geofenceService, applicationEventPublisher, auditService);
        ReflectionTestUtils.setField(collectionService, "denominationThresholdXaf", 0L);
        when(denominationLineRepository.findByCollectionId(any(UUID.class))).thenReturn(List.of());
        when(geofenceService.isWithinAssignedGeofence(any(), anyDouble(), anyDouble())).thenReturn(true);
        when(agentDirectoryService.findAuditInfo(any())).thenReturn(new AgentDirectoryService.AgentAuditInfo(UUID.randomUUID(), "agent1"));
        when(clientDirectoryService.findReceiptInfo(any())).thenReturn(new ClientDirectoryService.ClientReceiptInfo("M001", "Client One"));
    }

    private CollectionRequest validRequest(long amountXaf, List<DenominationLineDto> lines) {
        CollectionRequest request = new CollectionRequest();
        request.setClientId(clientId);
        request.setAmountXaf(amountXaf);
        request.setLat(4.05);
        request.setLon(9.70);
        request.setCollectedAt(Instant.now());
        request.setDeviceTxId("DEV-TX-1");
        request.setDenominationLines(lines);
        request.setPin("1234");
        return request;
    }

    private DenominationLineDto line(long faceValue, int qty) {
        DenominationLineDto dto = new DenominationLineDto();
        dto.setFaceValueXaf(faceValue);
        dto.setQuantity(qty);
        return dto;
    }

    @Test
    void recordsCollectionSuccessfully() {
        CollectionRequest request = validRequest(5000, List.of(line(5000, 1)));
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(100_000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        CollectionResponse response = collectionService.recordCollection(agentId, request);

        assertThat(response.getAmountXaf()).isEqualTo(5000);
        assertThat(response.getAgentId()).isEqualTo(agentId);
        assertThat(response.isDuplicate()).isFalse();

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("COLLECTION_RECORDED");
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.AGENT);
        assertThat(captor.getValue().getAgentId()).isEqualTo(agentId);
    }

    @Test
    void replaysIdempotentlyOnDuplicateDeviceTxId() {
        Collection existing = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(clientId)
                .amountXaf(5000).lat(4.05).lon(9.70).collectedAt(Instant.now()).deviceTxId("DEV-TX-1").build();
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.of(existing));

        CollectionResponse response = collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1))));

        assertThat(response.isDuplicate()).isTrue();
        assertThat(response.getId()).isEqualTo(existing.getId());
        verify(auditService, never()).record(any());
    }

    @Test
    void verifiesTransactionPinBeforeRecording() {
        CollectionRequest request = validRequest(5000, List.of(line(5000, 1)));
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(100_000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        collectionService.recordCollection(agentId, request);

        org.mockito.Mockito.verify(agentDirectoryService).verifyTransactionPin(agentId, "1234");
    }

    @Test
    void rejectsCollectionWhenTransactionPinIsWrong() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect PIN"))
                .when(agentDirectoryService).verifyTransactionPin(agentId, "1234");

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void checksScheduleWindowBeforeRecording() {
        CollectionRequest request = validRequest(5000, List.of(line(5000, 1)));
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(100_000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        collectionService.recordCollection(agentId, request);

        org.mockito.Mockito.verify(agentDirectoryService).requireWithinScheduleWindow(agentId, request.getCollectedAt());
    }

    @Test
    void rejectsCollectionOutsideBranchScheduleWindow() {
        // Login is no longer schedule-gated (AuthenticationControllerTest#testLoginSucceedsRegardlessOfBranchSchedule)
        // — the branch's open/close window is enforced here instead, checked against when the cash
        // was actually collected (collectedAt), not when this request happens to be processed.
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Outside authorized collection hours"))
                .when(agentDirectoryService).requireWithinScheduleWindow(any(), any());

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void doesNotCheckTransactionPinOnIdempotentReplay() {
        Collection existing = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(clientId)
                .amountXaf(5000).lat(4.05).lon(9.70).collectedAt(Instant.now()).deviceTxId("DEV-TX-1").build();
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.of(existing));

        collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1))));

        org.mockito.Mockito.verifyNoInteractions(agentDirectoryService);
    }

    @Test
    void rejectsCollectionWhenOutsideAssignedGeofence() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(geofenceService.isWithinAssignedGeofence(agentId, 4.05, 9.70)).thenReturn(false);

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");

        org.mockito.Mockito.verify(collectionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void allowsCollectionWhenAgentHasNoGeofenceAssigned() {
        CollectionRequest request = validRequest(5000, List.of(line(5000, 1)));
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(geofenceService.isWithinAssignedGeofence(agentId, 4.05, 9.70)).thenReturn(true);
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(100_000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        CollectionResponse response = collectionService.recordCollection(agentId, request);

        assertThat(response.isDuplicate()).isFalse();
    }

    @Test
    void locationNameStartsNullAndIsResolvedAsynchronously() {
        // Reverse-geocoding used to happen inline (a real blocking call to Nominatim) — it's now
        // dispatched to CollectionGeocodeListener via CollectionGeocodePublisher instead, so the
        // immediate response never carries a resolved name, only the async path fills it in later.
        CollectionRequest request = validRequest(5000, List.of(line(5000, 1)));
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(100_000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        CollectionResponse response = collectionService.recordCollection(agentId, request);

        assertThat(response.isDuplicate()).isFalse();
        assertThat(response.getLocationName()).isNull();
    }

    @Test
    void publishesGeocodeEventAfterSavingTheCollection() {
        // Published as a Spring application event, not sent to RabbitMQ directly — see
        // CollectionGeocodeEventRelay's own doc comment for why (a fast consumer could otherwise
        // race this method's own transaction commit under burst load).
        CollectionRequest request = validRequest(5000, List.of(line(5000, 1)));
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(100_000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        CollectionResponse response = collectionService.recordCollection(agentId, request);

        org.mockito.Mockito.verify(applicationEventPublisher).publishEvent(new CollectionGeocodeEvent(response.getId(), 4.05, 9.70));
    }

    @Test
    void doesNotPublishGeocodeEventOnIdempotentReplay() {
        Collection existing = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(clientId)
                .amountXaf(5000).lat(4.05).lon(9.70).collectedAt(Instant.now()).deviceTxId("DEV-TX-1").build();
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.of(existing));

        collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1))));

        org.mockito.Mockito.verifyNoInteractions(applicationEventPublisher);
    }

    @Test
    void rejectsUnknownClient() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId))
                .when(clientDirectoryService).requireActiveClient(clientId);

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void rejectsInactiveClient() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Client is not active: " + clientId))
                .when(clientDirectoryService).requireActiveClient(clientId);

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void rejectsWhenAgentHasPendingActivation() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(activationDirectoryService.hasPendingActivation(agentId)).thenReturn(true);

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void rejectsMissingDenominationBreakdownWhenRequired() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BR-02");
    }

    @Test
    void rejectsDenominationSumMismatch() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());

        // 1x1000 = 1000, but declared amount is 5000
        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(1000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsCollectionExceedingEscrowCeiling() {
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(3000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1)))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BR-03");
    }

    @Test
    void escrowCeilingIgnoresAlreadyReconciledCash() {
        // The actual fix: BR-03 must check cash-in-hand (unreconciled), not "collected today" —
        // an agent whose earlier collections were already reconciled (cash handed over) must be
        // able to keep collecting up to their ceiling again, even on the same calendar day.
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(10_000).build());
        // Reconciled collections aren't summed at all here — sumUnreconciledByAgent reports 0
        // even though the agent may have collected far more than the ceiling earlier today.
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);
        when(activationDirectoryService.sumUnreconciled(any(), any())).thenReturn(0L);

        CollectionResponse response = collectionService.recordCollection(agentId, validRequest(5000, List.of(line(5000, 1))));

        assertThat(response.getAmountXaf()).isEqualTo(5000);
    }

    @Test
    void allowsDenominationOptionalBelowThreshold() {
        ReflectionTestUtils.setField(collectionService, "denominationThresholdXaf", 1000L);
        when(collectionRepository.findByAgentIdAndDeviceTxId(agentId, "DEV-TX-1")).thenReturn(Optional.empty());
        when(escrowService.getStatus(agentId)).thenReturn(EscrowResponse.builder().effectiveCeilingXaf(100_000).build());
        when(collectionRepository.sumUnreconciledByAgent(any(), any())).thenReturn(0L);

        CollectionResponse response = collectionService.recordCollection(agentId, validRequest(500, null));

        assertThat(response.getAmountXaf()).isEqualTo(500);
    }

    @Test
    void findRecentByAgentResolvesClientNames() {
        Collection collection = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(clientId)
                .amountXaf(5000).lat(4.05).lon(9.70).collectedAt(Instant.now()).deviceTxId("DEV-TX-1").build();
        when(collectionRepository.findTop50ByAgentIdOrderByCollectedAtDesc(agentId)).thenReturn(List.of(collection));
        when(clientDirectoryService.findFullNames(any())).thenReturn(java.util.Map.of(clientId, "Jean Client"));

        List<CollectionResponse> results = collectionService.findRecentByAgent(agentId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getClientName()).isEqualTo("Jean Client");
        assertThat(results.get(0).getAmountXaf()).isEqualTo(5000);
    }

    @Test
    void findByAgentAndDayResolvesClientNamesAndOrdersNewestFirst() {
        java.time.LocalDate date = java.time.LocalDate.of(2026, 8, 20);
        Instant morning = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plus(9, java.time.temporal.ChronoUnit.HOURS);
        Instant afternoon = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().plus(15, java.time.temporal.ChronoUnit.HOURS);
        UUID otherClientId = UUID.randomUUID();
        Collection earlier = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(clientId)
                .amountXaf(3000).lat(4.05).lon(9.70).collectedAt(morning).deviceTxId("DEV-TX-1").build();
        Collection later = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(otherClientId)
                .amountXaf(7000).lat(4.05).lon(9.70).collectedAt(afternoon).deviceTxId("DEV-TX-2").build();
        when(collectionRepository.findByAgentIdInAndCollectedAtBetween(eq(List.of(agentId)), any(), any()))
                .thenReturn(List.of(earlier, later));
        when(clientDirectoryService.findFullNames(any())).thenReturn(java.util.Map.of(
                clientId, "Jean Client", otherClientId, "Marie Client"));

        List<CollectionResponse> results = collectionService.findByAgentAndDay(agentId, date);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getClientName()).isEqualTo("Marie Client");
        assertThat(results.get(0).getAmountXaf()).isEqualTo(7000);
        assertThat(results.get(1).getClientName()).isEqualTo("Jean Client");
        assertThat(results.get(1).getAmountXaf()).isEqualTo(3000);
    }
}
