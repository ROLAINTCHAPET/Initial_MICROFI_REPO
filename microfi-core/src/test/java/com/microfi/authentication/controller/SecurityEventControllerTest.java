package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.SecurityEvent;
import com.microfi.authentication.domain.SecurityEventType;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.authentication.service.SecurityEventService;
import com.microfi.savings.service.ClientDetailsService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = SecurityEventController.class)
@Import(SecurityConfig.class)
class SecurityEventControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SecurityEventService securityEventService;
    @MockitoBean
    private AgentRepository agentRepository;
    @MockitoBean
    private BranchRepository branchRepository;
    @MockitoBean
    private AdminUserRepository adminUserRepository;

    // SecurityConfig transitively needs JwtAuthenticationFilter's dependencies.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private AgentDetailsService agentDetailsService;
    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;
    @MockitoBean
    private ClientDetailsService clientDetailsService;

    private Authentication adminAuthentication(AdminRole role, UUID scopedBranchId) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).branchId(scopedBranchId).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void listOpenAdminSeesNetworkWide() {
        SecurityEvent event = SecurityEvent.builder().id(UUID.randomUUID()).agentId(UUID.randomUUID())
                .type(SecurityEventType.INSTALLATION_MISMATCH).createdAt(Instant.now()).build();
        when(securityEventService.listOpen(isNull())).thenReturn(List.of(event));
        when(agentRepository.findAllById(any())).thenReturn(List.of());
        when(branchRepository.findAllById(any())).thenReturn(List.of());
        when(adminUserRepository.findAllById(any())).thenReturn(List.of());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/security-events")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);

        verify(securityEventService).listOpen(isNull());
    }

    @Test
    void listOpenBranchManagerPinnedToOwnBranch() {
        UUID branchId = UUID.randomUUID();
        when(securityEventService.listOpen(branchId)).thenReturn(List.of());
        when(agentRepository.findAllById(any())).thenReturn(List.of());
        when(branchRepository.findAllById(any())).thenReturn(List.of());
        when(adminUserRepository.findAllById(any())).thenReturn(List.of());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/security-events")
                .exchange()
                .expectStatus().isOk();

        verify(securityEventService).listOpen(branchId);
    }

    @Test
    void listOpenCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER, UUID.randomUUID())))
                .get()
                .uri("/api/v1/admin/security-events")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void resolveRequiresReason() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/security-events/" + UUID.randomUUID() + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void resolveBranchManagerOutOfScopeForbidden() {
        UUID eventId = UUID.randomUUID();
        UUID eventBranchId = UUID.randomUUID();
        SecurityEvent event = SecurityEvent.builder().id(eventId).agentId(UUID.randomUUID()).branchId(eventBranchId)
                .type(SecurityEventType.INSTALLATION_MISMATCH).createdAt(Instant.now()).build();
        when(securityEventService.findByIdOrThrow(eventId)).thenReturn(event);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, UUID.randomUUID())))
                .patch()
                .uri("/api/v1/admin/security-events/" + eventId + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Cleared\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void resolveSucceedsForAdmin() {
        UUID eventId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        SecurityEvent event = SecurityEvent.builder().id(eventId).agentId(UUID.randomUUID())
                .type(SecurityEventType.INSTALLATION_MISMATCH).createdAt(Instant.now()).build();
        SecurityEvent resolved = SecurityEvent.builder().id(eventId).agentId(event.getAgentId())
                .type(SecurityEventType.INSTALLATION_MISMATCH).createdAt(event.getCreatedAt())
                .resolvedAt(Instant.now()).resolvedByAdminUserId(adminId).resolutionReason("Cleared").build();
        when(securityEventService.findByIdOrThrow(eventId)).thenReturn(event);
        when(securityEventService.resolve(eq(eventId), any(), anyString())).thenReturn(resolved);
        when(agentRepository.findAllById(any())).thenReturn(List.of());
        when(branchRepository.findAllById(any())).thenReturn(List.of());
        when(adminUserRepository.findAllById(any())).thenReturn(List.of());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/security-events/" + eventId + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Cleared\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.resolutionReason").isEqualTo("Cleared");
    }
}
