package com.microfi.transactions.controller;

import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.transactions.service.EscrowService;
import com.microfi.shared.dto.EscrowResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = EscrowController.class)
@Import(SecurityConfig.class)
class EscrowControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private EscrowService escrowService;

    // SecurityConfig (imported to exercise the real auth-required chain) transitively needs
    // JwtAuthenticationFilter's dependencies even though this controller doesn't use them.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    @WithMockUser
    void testGetStatus() {
        UUID agentId = UUID.randomUUID();
        EscrowResponse response = EscrowResponse.builder()
                .agentId(agentId).balanceXaf(50_000).baseCeilingXaf(50_000).effectiveCeilingXaf(50_000)
                .updatedAt(Instant.now()).build();
        when(escrowService.getStatus(agentId)).thenReturn(response);

        webTestClient.get()
                .uri("/api/v1/agents/" + agentId + "/escrow")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balanceXaf").isEqualTo(50000)
                .jsonPath("$.effectiveCeilingXaf").isEqualTo(50000);
    }

    @Test
    void testGetStatusUnauthenticatedRejected() {
        UUID agentId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/agents/" + agentId + "/escrow")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @WithMockUser
    void testTopUpSuccess() {
        UUID agentId = UUID.randomUUID();
        EscrowResponse response = EscrowResponse.builder()
                .agentId(agentId).balanceXaf(20_000).baseCeilingXaf(20_000).effectiveCeilingXaf(20_000)
                .updatedAt(Instant.now()).build();
        when(escrowService.topUp(eq(agentId), anyLong(), anyString())).thenReturn(response);

        webTestClient.post()
                .uri("/api/v1/agents/" + agentId + "/escrow/top-up")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amountXaf\":20000,\"reference\":\"MANUAL-CASHIER\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balanceXaf").isEqualTo(20000);
    }

    @Test
    @WithMockUser
    void testTopUpRejectsNonPositiveAmount() {
        UUID agentId = UUID.randomUUID();

        webTestClient.post()
                .uri("/api/v1/agents/" + agentId + "/escrow/top-up")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amountXaf\":0}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
