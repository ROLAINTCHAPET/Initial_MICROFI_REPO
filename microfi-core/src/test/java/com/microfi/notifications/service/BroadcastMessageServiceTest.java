package com.microfi.notifications.service;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.notifications.domain.BroadcastMessage;
import com.microfi.notifications.gateway.SmsGateway;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.gateway.SmsSendResult;
import com.microfi.notifications.repository.BroadcastMessageRepository;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.BroadcastMessageRequest;
import com.microfi.shared.dto.BroadcastMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BroadcastMessageServiceTest {

    @Mock
    private BroadcastMessageRepository broadcastMessageRepository;
    @Mock
    private AgentDirectoryService agentDirectoryService;
    @Mock
    private ClientDirectoryService clientDirectoryService;
    @Mock
    private SmsGatewayFactory smsGatewayFactory;
    @Mock
    private SmsGateway smsGateway;

    private BroadcastMessageService service;

    private final UUID branchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new BroadcastMessageService(broadcastMessageRepository, agentDirectoryService, clientDirectoryService, smsGatewayFactory);
        when(broadcastMessageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(smsGatewayFactory.getActiveGateway()).thenReturn(smsGateway);
        when(smsGateway.send(anyString(), anyString())).thenReturn(Mono.just(new SmsSendResult(true, "REF-1", null)));
    }

    private AdminUserDetails caller(AdminRole role, UUID callerBranchId) {
        return new AdminUserDetails(AdminUser.builder().id(UUID.randomUUID()).login("caller").role(role).branchId(callerBranchId).build());
    }

    private BroadcastMessageRequest request(String audience, UUID targetBranchId) {
        BroadcastMessageRequest request = new BroadcastMessageRequest();
        request.setAudience(audience);
        request.setBranchId(targetBranchId);
        request.setMessage("Please update your app");
        return request;
    }

    @Test
    void branchManagerBroadcastIsForcedToTheirOwnBranchRegardlessOfPayload() {
        when(agentDirectoryService.findAgentPhonesByBranch(branchId)).thenReturn(List.of("670000001", "670000002"));
        UUID someOtherBranch = UUID.randomUUID();

        BroadcastMessageResponse response = service.broadcast(caller(AdminRole.BRANCH_MANAGER, branchId), request("AGENTS", someOtherBranch));

        assertThat(response.getBranchId()).isEqualTo(branchId);
        assertThat(response.getRecipientCount()).isEqualTo(2);
        verify(agentDirectoryService, never()).findAllAgentPhones();
    }

    @Test
    void adminNetworkWideBroadcastToClientsUsesAllClientPhones() {
        when(clientDirectoryService.findAllClientPhones()).thenReturn(List.of("670000001", "670000002", "670000003"));

        BroadcastMessageResponse response = service.broadcast(caller(AdminRole.ADMIN, null), request("CLIENTS", null));

        assertThat(response.getBranchId()).isNull();
        assertThat(response.getAudience()).isEqualTo("CLIENTS");
        assertThat(response.getRecipientCount()).isEqualTo(3);
        verify(smsGateway, times(3)).send(anyString(), anyString());
    }

    @Test
    void adminBranchScopedBroadcastToAgentsUsesBranchPhonesOnly() {
        when(agentDirectoryService.findAgentPhonesByBranch(branchId)).thenReturn(List.of("670000001"));

        service.broadcast(caller(AdminRole.ADMIN, null), request("AGENTS", branchId));

        verify(agentDirectoryService).findAgentPhonesByBranch(branchId);
        verify(agentDirectoryService, never()).findAllAgentPhones();
    }

    @Test
    void historyIncludesTheRecipientCountPersistedAtSendTime() {
        BroadcastMessage saved = BroadcastMessage.builder().id(UUID.randomUUID()).senderLabel("admin")
                .audience(com.microfi.notifications.domain.BroadcastAudience.CLIENTS).branchId(null)
                .message("hi").recipientCount(16).createdAt(java.time.Instant.now()).build();
        when(broadcastMessageRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(saved));

        List<BroadcastMessageResponse> results = service.history(null, true);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRecipientCount()).isEqualTo(16);
    }

    @Test
    void mobilePollNeverExposesRecipientCount() {
        BroadcastMessage saved = BroadcastMessage.builder().id(UUID.randomUUID()).senderLabel("admin")
                .audience(com.microfi.notifications.domain.BroadcastAudience.AGENTS).branchId(branchId)
                .message("hi").recipientCount(5).createdAt(java.time.Instant.now()).build();
        when(broadcastMessageRepository.findRecentForAudienceAndBranch(any(), any(), any())).thenReturn(List.of(saved));

        List<BroadcastMessageResponse> results = service.listForAgents(branchId);

        assertThat(results.get(0).getRecipientCount()).isNull();
    }

    @Test
    void listForAgentsDelegatesToRepositoryWithAgentsAudience() {
        service.listForAgents(branchId);

        verify(broadcastMessageRepository).findRecentForAudienceAndBranch(
                eq(com.microfi.notifications.domain.BroadcastAudience.AGENTS), eq(branchId), any());
    }
}
