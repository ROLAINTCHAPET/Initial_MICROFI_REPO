package com.microfi.savings.controller;

import com.microfi.audit.service.AuditService;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.savings.ClientDetails;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.service.ClientActivationService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.shared.dto.ClientActivationPendingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ClientAuthenticationController.class)
@Import(SecurityConfig.class)
class ClientAuthenticationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientActivationService clientActivationService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    // SecurityConfig (imported to exercise the real permitAll chain for /api/v1/auth/**)
    // transitively needs JwtAuthenticationFilter's dependencies even though this controller
    // doesn't use them.
    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void testActivateSuccess() {
        when(clientActivationService.selfActivate(any())).thenReturn(
                ClientActivationPendingResponse.builder().clientId(UUID.randomUUID()).mfiMemberNo("M001").fullName("Jean Client").message("pending").build());

        webTestClient.post()
                .uri("/api/v1/auth/client/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mfiIdentifier\":\"M001\",\"login\":\"jean.client\",\"pin\":\"1234\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mfiMemberNo").isEqualTo("M001");
    }

    @Test
    void testActivateUnknownMfiIdentifierRejected() {
        when(clientActivationService.selfActivate(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "'BAD' isn't a recognised MICROFI account number"));

        webTestClient.post()
                .uri("/api/v1/auth/client/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mfiIdentifier\":\"BAD\",\"login\":\"jean.client\",\"pin\":\"1234\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testActivateInvalidPinFormatRejected() {
        webTestClient.post()
                .uri("/api/v1/auth/client/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mfiIdentifier\":\"M001\",\"login\":\"jean.client\",\"pin\":\"abc\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private ClientDetails clientDetails(String login, String pinHash) {
        ClientProfile client = ClientProfile.builder().id(UUID.randomUUID()).login(login).pinHash(pinHash).build();
        return new ClientDetails(client);
    }

    @Test
    void testLoginSuccess() {
        when(clientDetailsService.findByUsername("jean.client")).thenReturn(clientDetails("jean.client", "hashed"));
        when(passwordEncoder.matches("1234", "hashed")).thenReturn(true);
        when(jwtService.generateToken(any(), any())).thenReturn("mock-client-jwt");

        webTestClient.post()
                .uri("/api/v1/auth/client/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"jean.client\",\"pin\":\"1234\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-client-jwt");
    }

    @Test
    void testLoginWrongPinRejected() {
        when(clientDetailsService.findByUsername("jean.client")).thenReturn(clientDetails("jean.client", "hashed"));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        webTestClient.post()
                .uri("/api/v1/auth/client/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"jean.client\",\"pin\":\"0000\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginUnknownUserRejected() {
        when(clientDetailsService.findByUsername("ghost"))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("Client not found: ghost"));

        webTestClient.post()
                .uri("/api/v1/auth/client/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"ghost\",\"pin\":\"1234\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
