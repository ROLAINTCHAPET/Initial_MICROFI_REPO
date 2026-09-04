package com.microfi.transactions.service;

import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.notifications.gateway.SmsGateway;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.gateway.SmsSendResult;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.MiddlewareTransactionReversalResult;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.repository.CollectionRejectionRequestRepository;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionRejectionServiceTest {

    @Mock
    private CollectionRejectionRequestRepository collectionRejectionRequestRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private OfjAgentLineRepository ofjAgentLineRepository;
    @Mock
    private ClientDirectoryService clientDirectoryService;
    @Mock
    private CbsClientService cbsClientService;
    @Mock
    private SmsGatewayFactory smsGatewayFactory;
    @Mock
    private SmsGateway smsGateway;
    @Mock
    private AgentDirectoryService agentDirectoryService;
    @Mock
    private AuditService auditService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher applicationEventPublisher;

    private CollectionRejectionService service;

    private final UUID agentId = UUID.randomUUID();
    private final UUID collectionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CollectionRejectionService(collectionRejectionRequestRepository, collectionRepository,
                ofjAgentLineRepository, clientDirectoryService, cbsClientService, smsGatewayFactory,
                agentDirectoryService, auditService, applicationEventPublisher);
        when(collectionRejectionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Collection.CollectionBuilder collection() {
        return Collection.builder().id(collectionId).agentId(agentId).clientId(UUID.randomUUID())
                .amountXaf(5000).lat(4.05).lon(9.70).collectedAt(java.time.Instant.now()).deviceTxId("tx1");
    }

    @Test
    void requestRejectionCreatesPendingRequest() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection().build()));
        when(collectionRejectionRequestRepository.findByCollectionIdAndStatus(collectionId, CollectionRejectionStatus.PENDING)).thenReturn(Optional.empty());

        CollectionRejectionRequest result = service.requestRejection(agentId, collectionId, "Wrong amount entered", 4000L);

        assertThat(result.getStatus()).isEqualTo(CollectionRejectionStatus.PENDING);
        assertThat(result.getAgentId()).isEqualTo(agentId);
        assertThat(result.getReason()).isEqualTo("Wrong amount entered");
        // actualAmountXaf is snapshotted from the collection itself (5000, see collection()'s
        // builder), not whatever the caller happened to pass — only expectedAmountXaf comes from the agent.
        assertThat(result.getActualAmountXaf()).isEqualTo(5000L);
        assertThat(result.getExpectedAmountXaf()).isEqualTo(4000L);
    }

    /** UC pending: an admin/manager needs to see this the moment it's submitted — same instant-push reasoning as the SOS broadcaster. */
    @Test
    void requestRejectionPublishesAnAlertEvent() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection().build()));
        when(collectionRejectionRequestRepository.findByCollectionIdAndStatus(collectionId, CollectionRejectionStatus.PENDING)).thenReturn(Optional.empty());

        service.requestRejection(agentId, collectionId, "Wrong amount entered", null);

        org.mockito.ArgumentCaptor<com.microfi.events.CollectionRejectionRequestedEvent> captor =
                org.mockito.ArgumentCaptor.forClass(com.microfi.events.CollectionRejectionRequestedEvent.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().response().getAgentId()).isEqualTo(agentId);
        assertThat(captor.getValue().response().getReason()).isEqualTo("Wrong amount entered");
    }

    @Test
    void requestRejectionForbiddenForAnotherAgentsCollection() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(
                collection().agentId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.requestRejection(agentId, collectionId, "reason", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void requestRejectionConflictWhenAlreadyVoided() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(
                collection().voidedAt(java.time.Instant.now()).build()));

        assertThatThrownBy(() -> service.requestRejection(agentId, collectionId, "reason", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void requestRejectionConflictWhenAnotherRequestAlreadyPending() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection().build()));
        when(collectionRejectionRequestRepository.findByCollectionIdAndStatus(collectionId, CollectionRejectionStatus.PENDING))
                .thenReturn(Optional.of(CollectionRejectionRequest.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.requestRejection(agentId, collectionId, "reason", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void approveVoidsCollectionButSkipsReversalWhenNeverExported() {
        UUID requestId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection().build()));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollectionRejectionRequest result = service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID(), "admin1");

        assertThat(result.getStatus()).isEqualTo(CollectionRejectionStatus.APPROVED);
        assertThat(result.getProofPath()).isEqualTo("proofs/abc.pdf");
        ArgumentCaptor<Collection> captor = ArgumentCaptor.forClass(Collection.class);
        verify(collectionRepository).save(captor.capture());
        assertThat(captor.getValue().getVoidedAt()).isNotNull();
        verify(cbsClientService, never()).reverseTransaction(any(), any());
    }

    @Test
    void approveReversesCbsAndNotifiesClientWhenAlreadyExported() {
        UUID requestId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        Collection exported = collection().exportedAt(java.time.Instant.now()).cbsTransactionRef("CBSTX-123").build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(exported));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cbsClientService.reverseTransaction(eq("CBSTX-123"), anyString()))
                .thenReturn(Mono.just(MiddlewareTransactionReversalResult.builder().success(true).reversalReference("REV-CBSTX-123").build()));
        when(clientDirectoryService.findPhone(exported.getClientId())).thenReturn("237600000000");
        when(smsGatewayFactory.getActiveGateway()).thenReturn(smsGateway);
        when(smsGateway.send(anyString(), anyString())).thenReturn(Mono.just(new SmsSendResult(true, "msg-1", null)));

        service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID(), "admin1");

        verify(cbsClientService).reverseTransaction(eq("CBSTX-123"), anyString());
        verify(smsGateway).send(eq("237600000000"), anyString());
    }

    @Test
    void approveDoesNotThrowWhenCbsReversalFails() {
        UUID requestId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        Collection exported = collection().exportedAt(java.time.Instant.now()).cbsTransactionRef("CBSTX-123").build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(exported));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cbsClientService.reverseTransaction(any(), any())).thenReturn(Mono.error(new RuntimeException("CBS down")));
        when(clientDirectoryService.findPhone(exported.getClientId())).thenReturn("237600000000");
        when(smsGatewayFactory.getActiveGateway()).thenReturn(smsGateway);
        when(smsGateway.send(anyString(), anyString())).thenReturn(Mono.just(new SmsSendResult(true, "msg-1", null)));

        CollectionRejectionRequest result = service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID(), "admin1");

        assertThat(result.getStatus()).isEqualTo(CollectionRejectionStatus.APPROVED);
    }

    @Test
    void approveDebitsTheReconciliationLinesStoredTotalsByTheVoidedAmount() {
        UUID requestId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        Collection pendingConfirmation = collection().reconciledInLineId(lineId).build();
        OfjAgentLine line = OfjAgentLine.builder().id(lineId).ofjId(UUID.randomUUID()).agentId(agentId)
                .collectionsTotalXaf(20000L).digitalTotalXaf(20000L).build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(pendingConfirmation));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ofjAgentLineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(ofjAgentLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID(), "admin1");

        ArgumentCaptor<OfjAgentLine> captor = ArgumentCaptor.forClass(OfjAgentLine.class);
        verify(ofjAgentLineRepository).save(captor.capture());
        assertThat(captor.getValue().getCollectionsTotalXaf()).isEqualTo(15000L);
        assertThat(captor.getValue().getDigitalTotalXaf()).isEqualTo(15000L);
    }

    @Test
    void approveRequeuesUnexportedSiblingsBackToUnreconciled() {
        UUID requestId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        Collection rejected = collection().reconciledInLineId(lineId).build();
        Collection stillPendingSibling = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(UUID.randomUUID())
                .amountXaf(2000).collectedAt(java.time.Instant.now()).deviceTxId("tx2")
                .reconciledInLineId(lineId).reconciliationStatus(com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION)
                .build();
        Collection alreadyConfirmedSibling = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(UUID.randomUUID())
                .amountXaf(3000).collectedAt(java.time.Instant.now()).deviceTxId("tx3")
                .reconciledInLineId(lineId).reconciliationStatus(com.microfi.transactions.domain.CollectionReconciliationStatus.CONFIRMED)
                .reconciledAt(java.time.Instant.now()).confirmedBy(com.microfi.transactions.domain.CollectionConfirmedBy.AGENT)
                .build();
        OfjAgentLine line = OfjAgentLine.builder().id(lineId).ofjId(UUID.randomUUID()).agentId(agentId)
                .collectionsTotalXaf(10000L).digitalTotalXaf(10000L).build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(rejected));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRepository.findByReconciledInLineId(lineId))
                .thenReturn(java.util.List.of(rejected, stillPendingSibling, alreadyConfirmedSibling));
        when(ofjAgentLineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(ofjAgentLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(UUID.randomUUID());

        service.approve(requestId, "proofs/abc.pdf", reviewerId, "admin1");

        assertThat(stillPendingSibling.getReconciliationStatus()).isEqualTo(com.microfi.transactions.domain.CollectionReconciliationStatus.UNRECONCILED);
        assertThat(stillPendingSibling.getReconciledInLineId()).isNull();
        assertThat(alreadyConfirmedSibling.getReconciliationStatus()).isEqualTo(com.microfi.transactions.domain.CollectionReconciliationStatus.UNRECONCILED);
        assertThat(alreadyConfirmedSibling.getReconciledInLineId()).isNull();
        assertThat(alreadyConfirmedSibling.getReconciledAt()).isNull();
        assertThat(alreadyConfirmedSibling.getConfirmedBy()).isNull();

        ArgumentCaptor<OfjAgentLine> captor = ArgumentCaptor.forClass(OfjAgentLine.class);
        // One combined debit — the rejected collection's own 5000 plus the two requeued siblings'
        // 2000+3000 — not two separate saves: 10000 - 5000 - 2000 - 3000 = 0.
        verify(ofjAgentLineRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        assertThat(captor.getValue().getCollectionsTotalXaf()).isEqualTo(0L);
        assertThat(captor.getValue().getDigitalTotalXaf()).isEqualTo(0L);
        // Regression: nothing legitimately confirmed remains on the line at all (digitalTotalXaf
        // dropped to 0) — physicalTotalXaf/deltaXaf must be zeroed too, or the line would keep
        // showing a stale physical count as if it were still validated against something real.
        assertThat(captor.getValue().getPhysicalTotalXaf()).isEqualTo(0L);
        assertThat(captor.getValue().getDeltaXaf()).isEqualTo(0L);
        verify(auditService).record(org.mockito.ArgumentMatchers.argThat(entry ->
                "COLLECTION_REJECTION_SIBLINGS_REQUEUED".equals(entry.getEventType())));
    }

    /**
     * Mixed-sweep case: some OTHER, still-legitimate confirmed content remains on the line after
     * the rejected collection and its unexported siblings are removed — physicalTotalXaf must be
     * left alone here, since there's no reliable way to know which portion of it belongs to the
     * voided/requeued part versus the part that's genuinely still valid.
     */
    @Test
    void approveLeavesPhysicalTotalAloneWhenSomeLegitimateContentRemainsOnTheLine() {
        UUID requestId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        Collection rejected = collection().reconciledInLineId(lineId).build();
        // No siblings returned at all here (simulates an already-exported sibling being excluded,
        // or none existing) — only the rejected collection's own 5000 is debited from a much
        // larger line, leaving digitalTotalXaf comfortably positive.
        OfjAgentLine line = OfjAgentLine.builder().id(lineId).ofjId(UUID.randomUUID()).agentId(agentId)
                .collectionsTotalXaf(50000L).digitalTotalXaf(50000L).physicalTotalXaf(50000L).deltaXaf(0L).build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(rejected));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRepository.findByReconciledInLineId(lineId)).thenReturn(java.util.List.of(rejected));
        when(ofjAgentLineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(ofjAgentLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID(), "admin1");

        ArgumentCaptor<OfjAgentLine> captor = ArgumentCaptor.forClass(OfjAgentLine.class);
        verify(ofjAgentLineRepository).save(captor.capture());
        assertThat(captor.getValue().getDigitalTotalXaf()).isEqualTo(45000L);
        assertThat(captor.getValue().getPhysicalTotalXaf()).isEqualTo(50000L);
        assertThat(captor.getValue().getDeltaXaf()).isEqualTo(0L);
    }

    @Test
    void approveLeavesAlreadyExportedSiblingsCompletelyUntouched() {
        UUID requestId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        Collection rejected = collection().reconciledInLineId(lineId).build();
        Collection alreadyExportedSibling = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(UUID.randomUUID())
                .amountXaf(4000).collectedAt(java.time.Instant.now()).deviceTxId("tx4")
                .reconciledInLineId(lineId).reconciliationStatus(com.microfi.transactions.domain.CollectionReconciliationStatus.CONFIRMED)
                .exportedAt(java.time.Instant.now()).cbsTransactionRef("CBSTX-999")
                .build();
        OfjAgentLine line = OfjAgentLine.builder().id(lineId).ofjId(UUID.randomUUID()).agentId(agentId)
                .collectionsTotalXaf(9000L).digitalTotalXaf(9000L).build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(rejected));
        when(collectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(collectionRepository.findByReconciledInLineId(lineId)).thenReturn(java.util.List.of(rejected, alreadyExportedSibling));
        when(ofjAgentLineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(ofjAgentLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID(), "admin1");

        assertThat(alreadyExportedSibling.getReconciliationStatus()).isEqualTo(com.microfi.transactions.domain.CollectionReconciliationStatus.CONFIRMED);
        assertThat(alreadyExportedSibling.getReconciledInLineId()).isEqualTo(lineId);
        verify(collectionRepository, never()).saveAll(any());
        // Only the rejected collection's own 5000 is debited — the exported sibling's 4000 stays.
        ArgumentCaptor<OfjAgentLine> captor = ArgumentCaptor.forClass(OfjAgentLine.class);
        verify(ofjAgentLineRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        assertThat(captor.getValue().getCollectionsTotalXaf()).isEqualTo(4000L);
    }

    @Test
    void approveConflictWhenAlreadyDecided() {
        UUID requestId = UUID.randomUUID();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(
                CollectionRejectionRequest.builder().id(requestId).status(CollectionRejectionStatus.DENIED).build()));

        assertThatThrownBy(() -> service.approve(requestId, "proof.pdf", UUID.randomUUID(), "admin1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void denyLeavesCollectionUntouched() {
        UUID requestId = UUID.randomUUID();
        CollectionRejectionRequest request = CollectionRejectionRequest.builder().id(requestId).collectionId(collectionId)
                .agentId(agentId).status(CollectionRejectionStatus.PENDING).build();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        CollectionRejectionRequest result = service.deny(requestId, "Not enough evidence", UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(CollectionRejectionStatus.DENIED);
        assertThat(result.getDecisionReason()).isEqualTo("Not enough evidence");
        verify(collectionRepository, never()).save(any());
    }
}
