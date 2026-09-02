package com.microfi.savings.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.authentication.service.JwtService;
import com.microfi.savings.ClientDetails;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.service.ClientActivationService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.shared.dto.ClientActivationResponse;
import com.microfi.shared.dto.PendingActivationRequestResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ClientActivationController.class)
@Import(SecurityConfig.class)
class ClientActivationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientActivationService clientActivationService;

    // SecurityConfig (imported to exercise the real auth-required chain) transitively needs
    // JwtAuthenticationFilter's dependencies even though this controller doesn't use them.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    @MockitoBean
    private AgentDirectoryService agentDirectoryService;

    @MockitoBean
    private AuditService auditService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private static final String LOGIN = "jean.client";

    private Authentication agentAuthentication() {
        Agent agent = Agent.builder().id(agentId).employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private Authentication clientAuthentication() {
        ClientProfile client = ClientProfile.builder().id(clientId).login(LOGIN).build();
        ClientDetails details = new ClientDetails(client);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private Authentication adminAuthentication(AdminRole role) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void testSponsorSuccess_resolvesAgentFromPrincipal() {
        when(clientActivationService.sponsorActivation(eq(LOGIN), eq(agentId))).thenReturn(
                ClientActivationResponse.builder().clientId(clientId).status("AWAITING_PAYMENT").build());
        when(agentDirectoryService.findAuditInfo(agentId)).thenReturn(new AgentDirectoryService.AgentAuditInfo(UUID.randomUUID(), "agent1"));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/clients/activation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"" + LOGIN + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("AWAITING_PAYMENT");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("CLIENT_ACTIVATION_SPONSORED");
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.AGENT);
        assertThat(captor.getValue().getActorId()).isEqualTo(agentId);
    }

    @Test
    void testSponsorUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/clients/activation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"" + LOGIN + "\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testConfirmPaymentSuccess_resolvesClientFromPrincipal() {
        when(clientActivationService.confirmPayment(eq(clientId), any())).thenReturn(
                ClientActivationResponse.builder().clientId(clientId).status("ACTIVE")
                        .agentCommissionXaf(300L).mfiShareXaf(700L).paymentReference("FEE-1").build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(clientAuthentication()))
                .post()
                .uri("/api/v1/clients/me/activation/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pin\":\"1234\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.paymentReference").isEqualTo("FEE-1");
    }

    @Test
    void testConfirmPaymentUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/clients/me/activation/pay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pin\":\"1234\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testListPendingSuccess_ownBranch() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(clientActivationService.listPendingForAgent(agentId)).thenReturn(List.of(
                PendingActivationRequestResponse.builder().id(UUID.randomUUID()).clientId(clientId).agentId(agentId)
                        .createdAt(Instant.now()).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/activation-requests/pending")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void testListPendingOutsideBranchForbidden() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(UUID.randomUUID());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/activation-requests/pending")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testCancelPendingSuccess() {
        UUID requestId = UUID.randomUUID();
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(clientActivationService.cancelActivationRequest(eq(requestId), any(), any())).thenReturn(
                PendingActivationRequestResponse.builder().id(requestId).clientId(clientId).agentId(agentId).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + agentId + "/activation-requests/" + requestId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Client unreachable\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(requestId.toString());
    }

    @Test
    void testCancelPendingCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .patch()
                .uri("/api/v1/admin/agents/" + agentId + "/activation-requests/" + UUID.randomUUID() + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"x\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testCancelPendingMissingReasonRejected() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + agentId + "/activation-requests/" + UUID.randomUUID() + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
