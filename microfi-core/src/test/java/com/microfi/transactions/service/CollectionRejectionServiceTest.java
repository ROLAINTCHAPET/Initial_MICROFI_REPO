package com.microfi.transactions.service;

import com.microfi.cbsclient.CbsClientService;
import com.microfi.notifications.gateway.SmsGateway;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.gateway.SmsSendResult;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.MiddlewareTransactionReversalResult;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import com.microfi.transactions.repository.CollectionRejectionRequestRepository;
import com.microfi.transactions.repository.CollectionRepository;
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
    private ClientDirectoryService clientDirectoryService;
    @Mock
    private CbsClientService cbsClientService;
    @Mock
    private SmsGatewayFactory smsGatewayFactory;
    @Mock
    private SmsGateway smsGateway;

    private CollectionRejectionService service;

    private final UUID agentId = UUID.randomUUID();
    private final UUID collectionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CollectionRejectionService(collectionRejectionRequestRepository, collectionRepository,
                clientDirectoryService, cbsClientService, smsGatewayFactory);
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

        CollectionRejectionRequest result = service.requestRejection(agentId, collectionId, "Wrong amount entered");

        assertThat(result.getStatus()).isEqualTo(CollectionRejectionStatus.PENDING);
        assertThat(result.getAgentId()).isEqualTo(agentId);
        assertThat(result.getReason()).isEqualTo("Wrong amount entered");
    }

    @Test
    void requestRejectionForbiddenForAnotherAgentsCollection() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(
                collection().agentId(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.requestRejection(agentId, collectionId, "reason"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void requestRejectionConflictWhenAlreadyVoided() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(
                collection().voidedAt(java.time.Instant.now()).build()));

        assertThatThrownBy(() -> service.requestRejection(agentId, collectionId, "reason"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void requestRejectionConflictWhenAnotherRequestAlreadyPending() {
        when(collectionRepository.findById(collectionId)).thenReturn(Optional.of(collection().build()));
        when(collectionRejectionRequestRepository.findByCollectionIdAndStatus(collectionId, CollectionRejectionStatus.PENDING))
                .thenReturn(Optional.of(CollectionRejectionRequest.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> service.requestRejection(agentId, collectionId, "reason"))
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

        CollectionRejectionRequest result = service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID());

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

        service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID());

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

        CollectionRejectionRequest result = service.approve(requestId, "proofs/abc.pdf", UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo(CollectionRejectionStatus.APPROVED);
    }

    @Test
    void approveConflictWhenAlreadyDecided() {
        UUID requestId = UUID.randomUUID();
        when(collectionRejectionRequestRepository.findById(requestId)).thenReturn(Optional.of(
                CollectionRejectionRequest.builder().id(requestId).status(CollectionRejectionStatus.DENIED).build()));

        assertThatThrownBy(() -> service.approve(requestId, "proof.pdf", UUID.randomUUID()))
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
