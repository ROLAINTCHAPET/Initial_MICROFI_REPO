package com.microfi.savings.controller;

import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.ClientDetails;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.savings.service.ClientSelfService;
import com.microfi.shared.dto.ClientProfileSelfResponse;
import com.microfi.shared.dto.MiddlewareBalance;
import com.microfi.shared.dto.MiddlewareHistoryEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ClientSelfServiceController.class)
@Import(SecurityConfig.class)
class ClientSelfServiceControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientSelfService clientSelfService;

    @MockitoBean
    private CbsClientService cbsClientService;

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

    private final UUID clientId = UUID.randomUUID();

    private Authentication clientAuthentication() {
        ClientProfile client = ClientProfile.builder().id(clientId).login("jean.client").cbsRef("CBS-1").build();
        ClientDetails details = new ClientDetails(client);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void testProfileSuccess_resolvesClientFromPrincipal() {
        when(clientSelfService.getProfile(clientId)).thenReturn(
                ClientProfileSelfResponse.builder().id(clientId).mfiMemberNo("M001").fullName("Jean Client").tokenStatus("ACTIVE").build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(clientAuthentication()))
                .get()
                .uri("/api/v1/clients/me/profile")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mfiMemberNo").isEqualTo("M001")
                .jsonPath("$.tokenStatus").isEqualTo("ACTIVE");
    }

    @Test
    void testProfileUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/clients/me/profile")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testBalanceSuccess() {
        when(clientSelfService.getCbsRef(clientId)).thenReturn("CBS-1");
        when(cbsClientService.getBalance("CBS-1")).thenReturn(
                Mono.just(MiddlewareBalance.builder().memberId("CBS-1").balanceXaf(45_000).asOf(Instant.now()).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(clientAuthentication()))
                .get()
                .uri("/api/v1/clients/me/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balanceXaf").isEqualTo(45000);
    }

    @Test
    void testHistorySuccess() {
        when(clientSelfService.getCbsRef(clientId)).thenReturn("CBS-1");
        when(cbsClientService.getHistory("CBS-1")).thenReturn(
                Flux.just(MiddlewareHistoryEntry.builder().reference("CBSTX-1").amountXaf(5000).date(Instant.now()).type("DEPOSIT").build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(clientAuthentication()))
                .get()
                .uri("/api/v1/clients/me/history")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }
}
