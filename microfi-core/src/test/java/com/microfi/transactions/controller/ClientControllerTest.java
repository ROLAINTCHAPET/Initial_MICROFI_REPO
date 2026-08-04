package com.microfi.transactions.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.transactions.domain.ClientProfile;
import com.microfi.transactions.repository.ClientProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ClientController.class)
@Import(SecurityConfig.class)
class ClientControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ClientProfileRepository clientProfileRepository;

    // SecurityConfig (imported to exercise the real auth-required chain) transitively needs
    // JwtAuthenticationFilter's dependencies even though this controller doesn't use them.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    private Authentication adminAuthentication(AdminRole role) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    @WithMockUser
    void testLookupReturnsMatches() {
        ClientProfile client = ClientProfile.builder().id(UUID.randomUUID()).mfiMemberNo("M001").fullName("Jean Client").build();
        when(clientProfileRepository.search("Jean")).thenReturn(List.of(client));

        webTestClient.get()
                .uri("/api/v1/clients/lookup?query=Jean")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void testLookupUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/clients/lookup?query=Jean")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testCreateClientSuccess() {
        when(clientProfileRepository.existsByMfiMemberNo("M001")).thenReturn(false);
        when(clientProfileRepository.save(any(ClientProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mfiMemberNo\":\"M001\",\"fullName\":\"Jean Client\",\"phone\":\"+237600000001\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.mfiMemberNo").isEqualTo("M001")
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void testCreateClientDuplicateConflict() {
        when(clientProfileRepository.existsByMfiMemberNo("M001")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mfiMemberNo\":\"M001\",\"fullName\":\"Jean Client\"}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testCreateClientUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/admin/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mfiMemberNo\":\"M001\",\"fullName\":\"Jean Client\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testCreateClientCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .post()
                .uri("/api/v1/admin/clients")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"mfiMemberNo\":\"M001\",\"fullName\":\"Jean Client\"}")
                .exchange()
                .expectStatus().isForbidden();
    }
}
