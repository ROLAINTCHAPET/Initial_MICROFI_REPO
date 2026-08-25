package com.microfi.notifications.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.authentication.service.AgentDirectoryService.AgentReceiptInfo;
import com.microfi.notifications.domain.NotificationChannel;
import com.microfi.notifications.domain.NotificationLog;
import com.microfi.notifications.domain.NotificationStatus;
import com.microfi.notifications.gateway.SmsGateway;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.gateway.SmsSendResult;
import com.microfi.notifications.repository.BranchNoticeRepository;
import com.microfi.notifications.repository.NotificationLogRepository;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.savings.service.ClientDirectoryService.ClientReceiptInfo;
import com.microfi.shared.dto.NotificationLogResponse;
import com.microfi.shared.dto.NotifyCollectionRequest;
import com.microfi.transactions.service.CollectionDirectoryService;
import com.microfi.transactions.service.CollectionSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private BranchNoticeRepository branchNoticeRepository;
    @Mock
    private CollectionDirectoryService collectionDirectoryService;
    @Mock
    private ClientDirectoryService clientDirectoryService;
    @Mock
    private AgentDirectoryService agentDirectoryService;
    @Mock
    private SmsGatewayFactory smsGatewayFactory;
    @Mock
    private SmsGateway smsGateway;
    @Mock
    private MfiSettingsService mfiSettingsService;

    private NotificationService notificationService;

    private final UUID collectionId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        notificationService = new NotificationService(notificationLogRepository, branchNoticeRepository, collectionDirectoryService,
                clientDirectoryService, agentDirectoryService, smsGatewayFactory, mfiSettingsService);
        when(smsGatewayFactory.getActiveGateway()).thenReturn(smsGateway);
        when(mfiSettingsService.getName()).thenReturn("MICROFI");
        when(notificationLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private CollectionSummary collectionOwnedBy(UUID owningAgentId) {
        return new CollectionSummary(collectionId, owningAgentId, clientId, 5000L, null, Instant.now(), 3.87, 11.52, "device-tx-1");
    }

    private void stubReceiptLookups(UUID owningAgentId) {
        when(agentDirectoryService.findReceiptInfo(owningAgentId)).thenReturn(new AgentReceiptInfo("AGT001", "Test Agent", "Test Branch"));
        when(clientDirectoryService.findReceiptInfo(clientId)).thenReturn(new ClientReceiptInfo("MFI-001", "Test Client"));
        when(collectionDirectoryService.findDenominationLines(collectionId)).thenReturn(java.util.List.of());
    }

    @Test
    void notifySendsAndLogsSuccess() {
        when(collectionDirectoryService.findById(collectionId)).thenReturn(collectionOwnedBy(agentId));
        when(clientDirectoryService.findPhone(clientId)).thenReturn("237611111111");
        stubReceiptLookups(agentId);
        when(smsGateway.send(anyString(), anyString())).thenReturn(Mono.just(new SmsSendResult(true, "ref-1", null)));

        NotifyCollectionRequest request = new NotifyCollectionRequest();
        request.setPrintedReceipt(true);

        NotificationLogResponse response = notificationService.notifyCollection(collectionId, agentId, request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.isPrintedReceipt()).isTrue();
        assertThat(response.getCollectionId()).isEqualTo(collectionId);
        assertThat(response.getReceiptText()).contains("5 000 XAF").contains("AGT001");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        org.mockito.Mockito.verify(notificationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientPhone()).isEqualTo("237611111111");
        assertThat(captor.getValue().getProviderReference()).isEqualTo("ref-1");
    }

    @Test
    void notifyLogsFailureWithoutThrowingWhenGatewayFails() {
        when(collectionDirectoryService.findById(collectionId)).thenReturn(collectionOwnedBy(agentId));
        when(clientDirectoryService.findPhone(clientId)).thenReturn("237611111111");
        stubReceiptLookups(agentId);
        when(smsGateway.send(anyString(), anyString())).thenReturn(Mono.just(new SmsSendResult(false, null, "timeout")));

        NotificationLogResponse response = notificationService.notifyCollection(collectionId, agentId, new NotifyCollectionRequest()).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void notifyRejectsWhenCallerDidNotRecordTheCollection() {
        UUID otherAgentId = UUID.randomUUID();
        when(collectionDirectoryService.findById(collectionId)).thenReturn(collectionOwnedBy(otherAgentId));

        assertThatThrownBy(() -> notificationService.notifyCollection(collectionId, agentId, new NotifyCollectionRequest()).block())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void listForCollectionSkipsOwnershipCheckForAdmin() {
        NotificationLog log = NotificationLog.builder().id(UUID.randomUUID()).collectionId(collectionId)
                .channel(NotificationChannel.SMS).status(NotificationStatus.SENT).printedReceipt(false).sentAt(Instant.now()).build();
        when(notificationLogRepository.findByCollectionIdOrderBySentAtDesc(collectionId)).thenReturn(java.util.List.of(log));

        java.util.List<NotificationLogResponse> result = notificationService.listForCollection(collectionId, null);

        assertThat(result).hasSize(1);
        org.mockito.Mockito.verifyNoInteractions(collectionDirectoryService);
    }

    @Test
    void listForCollectionAllowsOwningAgent() {
        when(collectionDirectoryService.findById(collectionId)).thenReturn(collectionOwnedBy(agentId));
        when(notificationLogRepository.findByCollectionIdOrderBySentAtDesc(collectionId)).thenReturn(java.util.List.of());

        java.util.List<NotificationLogResponse> result = notificationService.listForCollection(collectionId, agentId);

        assertThat(result).isEmpty();
    }

    @Test
    void listForCollectionRejectsNonOwningAgent() {
        UUID otherAgentId = UUID.randomUUID();
        when(collectionDirectoryService.findById(collectionId)).thenReturn(collectionOwnedBy(otherAgentId));

        assertThatThrownBy(() -> notificationService.listForCollection(collectionId, agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
