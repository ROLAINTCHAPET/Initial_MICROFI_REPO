package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.authentication.service.JwtService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.shared.dto.LocationPingResponse;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.service.TrackingService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = TrackingController.class)
@Import(SecurityConfig.class)
class TrackingControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TrackingService trackingService;

    @MockitoBean
    private AuditService auditService;

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

    private final UUID agentId = UUID.randomUUID();
    private final UUID otherAgentId = UUID.randomUUID();

    private Authentication agentAuthentication() {
        Agent agent = Agent.builder().id(agentId).employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void testLocationSuccess_resolvesAgentFromPrincipal() {
        when(trackingService.recordLocation(eq(agentId), any())).thenReturn(
                LocationPingResponse.builder().id(UUID.randomUUID()).agentId(agentId).lat(4.05).lon(9.70).recordedAt(Instant.now()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/agents/" + agentId + "/location")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"lat\":4.05,\"lon\":9.70}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agentId").isEqualTo(agentId.toString());
    }

    @Test
    void testLocationUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/agents/" + agentId + "/location")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"lat\":4.05,\"lon\":9.70}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLocationMissingGpsRejected() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/agents/" + agentId + "/location")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testLocationForAnotherAgentForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/agents/" + otherAgentId + "/location")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"lat\":4.05,\"lon\":9.70}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSosSuccessWithGps() {
        when(trackingService.raiseSos(eq(agentId), any())).thenReturn(
                SosResponse.builder().id(UUID.randomUUID()).agentId(agentId).lat(4.05).lon(9.70).raisedAt(Instant.now()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/agents/" + agentId + "/sos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"lat\":4.05,\"lon\":9.70}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agentId").isEqualTo(agentId.toString());

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.AGENT);
        assertThat(captor.getValue().getAgentId()).isEqualTo(agentId);
        assertThat(captor.getValue().getEventType()).isEqualTo("AGENT_SOS_TRIGGERED");
    }

    @Test
    void testSosAcceptedWithoutGps() {
        when(trackingService.raiseSos(eq(agentId), any())).thenReturn(
                SosResponse.builder().id(UUID.randomUUID()).agentId(agentId).raisedAt(Instant.now()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/agents/" + agentId + "/sos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testSosUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/agents/" + agentId + "/sos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testSosForAnotherAgentForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/agents/" + otherAgentId + "/sos")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSyncStatusSuccess_resolvesAgentFromPrincipal() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .patch()
                .uri("/api/v1/agents/" + agentId + "/sync-status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pendingCount\":3}")
                .exchange()
                .expectStatus().isNoContent();

        org.mockito.Mockito.verify(agentDirectoryService).updateSyncStatus(agentId, 3);
    }

    @Test
    void testSyncStatusUnauthenticatedRejected() {
        webTestClient.patch()
                .uri("/api/v1/agents/" + agentId + "/sync-status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pendingCount\":0}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testSyncStatusForAnotherAgentForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .patch()
                .uri("/api/v1/agents/" + otherAgentId + "/sync-status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pendingCount\":0}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSyncStatusNegativeCountRejected() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .patch()
                .uri("/api/v1/agents/" + agentId + "/sync-status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pendingCount\":-1}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
