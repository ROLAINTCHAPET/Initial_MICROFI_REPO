package com.microfi.transactions.controller;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.CollectionResponse;
import com.microfi.transactions.service.CollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = CollectionController.class)
@Import(SecurityConfig.class)
class CollectionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CollectionService collectionService;

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

    private final UUID agentId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    private Authentication agentAuthentication() {
        Agent agent = Agent.builder().id(agentId).employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private String requestBody() {
        return "{\"clientId\":\"" + clientId + "\",\"amountXaf\":5000,\"lat\":4.05,\"lon\":9.70," +
                "\"collectedAt\":\"" + Instant.now() + "\",\"deviceTxId\":\"DEV-1\"," +
                "\"denominationLines\":[{\"faceValueXaf\":5000,\"quantity\":1}]}";
    }

    @Test
    void testCreateSuccess_resolvesAgentFromPrincipal() {
        CollectionResponse response = CollectionResponse.builder()
                .id(UUID.randomUUID()).agentId(agentId).clientId(clientId).amountXaf(5000).build();
        when(collectionService.recordCollection(eq(agentId), any())).thenReturn(response);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agentId").isEqualTo(agentId.toString())
                .jsonPath("$.amountXaf").isEqualTo(5000);
    }

    @Test
    void testCreateUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testCreateMissingGpsRejected() {
        String noGps = "{\"clientId\":\"" + clientId + "\",\"amountXaf\":5000," +
                "\"collectedAt\":\"" + Instant.now() + "\",\"deviceTxId\":\"DEV-1\"}";

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(noGps)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testSyncProcessesEachItemIndependently() {
        CollectionResponse okResponse = CollectionResponse.builder()
                .id(UUID.randomUUID()).agentId(agentId).clientId(clientId).amountXaf(5000).deviceTxId("DEV-OK").build();
        when(collectionService.recordCollection(eq(agentId), argThatDeviceTxId("DEV-OK"))).thenReturn(okResponse);
        when(collectionService.recordCollection(eq(agentId), argThatDeviceTxId("DEV-FAIL")))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "would exceed ceiling"));

        String batch = "[" +
                "{\"clientId\":\"" + clientId + "\",\"amountXaf\":5000,\"lat\":4.05,\"lon\":9.70,\"collectedAt\":\"" + Instant.now() + "\",\"deviceTxId\":\"DEV-OK\",\"denominationLines\":[{\"faceValueXaf\":5000,\"quantity\":1}]}," +
                "{\"clientId\":\"" + clientId + "\",\"amountXaf\":9000,\"lat\":4.05,\"lon\":9.70,\"collectedAt\":\"" + Instant.now() + "\",\"deviceTxId\":\"DEV-FAIL\",\"denominationLines\":[{\"faceValueXaf\":9000,\"quantity\":1}]}" +
                "]";

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/collections/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(batch)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(2);
    }

    private com.microfi.shared.dto.CollectionRequest argThatDeviceTxId(String deviceTxId) {
        return org.mockito.ArgumentMatchers.argThat(req -> req != null && deviceTxId.equals(req.getDeviceTxId()));
    }
}
