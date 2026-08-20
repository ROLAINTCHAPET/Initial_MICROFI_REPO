package com.microfi.notifications.controller;

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
import com.microfi.authentication.service.JwtService;
import com.microfi.notifications.service.NotificationService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.shared.dto.NotificationLogResponse;
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
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID collectionId = UUID.randomUUID();

    private Authentication agentAuthentication() {
        Agent agent = Agent.builder().id(agentId).employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private Authentication adminAuthentication() {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(AdminRole.ADMIN).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void notifySuccess() {
        when(notificationService.notifyCollection(eq(collectionId), eq(agentId), any())).thenReturn(Mono.just(
                NotificationLogResponse.builder().id(UUID.randomUUID()).collectionId(collectionId)
                        .channel("SMS").status("SENT").sentAt(Instant.now()).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .post()
                .uri("/api/v1/collections/" + collectionId + "/notify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"printedReceipt\":false}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SENT")
                .jsonPath("$.channel").isEqualTo("SMS");
    }

    @Test
    void notifyUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/collections/" + collectionId + "/notify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"printedReceipt\":false}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listNotificationsAsOwningAgent() {
        when(notificationService.listForCollection(collectionId, agentId)).thenReturn(java.util.List.of(
                NotificationLogResponse.builder().id(UUID.randomUUID()).collectionId(collectionId).channel("SMS").status("SENT").sentAt(Instant.now()).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .get()
                .uri("/api/v1/collections/" + collectionId + "/notifications")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void listNotificationsAsAdminSkipsOwnershipCheck() {
        when(notificationService.listForCollection(eq(collectionId), org.mockito.ArgumentMatchers.isNull())).thenReturn(java.util.List.of());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication()))
                .get()
                .uri("/api/v1/collections/" + collectionId + "/notifications")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void listNotificationsUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/collections/" + collectionId + "/notifications")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
