package com.microfi.transactions.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.authentication.service.JwtService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.shared.dto.CollectionResponse;
import com.microfi.transactions.service.CollectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminAgentCollectionsController.class)
@Import(SecurityConfig.class)
class AdminAgentCollectionsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CollectionService collectionService;

    @MockitoBean
    private AgentDirectoryService agentDirectoryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private Authentication adminAuthentication(AdminRole role, UUID callerBranchId) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).branchId(callerBranchId).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void collectionsSucceedsForOwnBranchCashier() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(collectionService.findByAgentAndDay(eq(agentId), eq(LocalDate.of(2026, 1, 15)))).thenReturn(List.of(
                CollectionResponse.builder().id(UUID.randomUUID()).agentId(agentId).clientName("Jean Client")
                        .amountXaf(5000).collectedAt(Instant.now()).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER, branchId)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/collections?date=2026-01-15")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].clientName").isEqualTo("Jean Client")
                .jsonPath("$[0].amountXaf").isEqualTo(5000);
    }

    @Test
    void collectionsSucceedsForAdminAcrossBranches() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(UUID.randomUUID());
        when(collectionService.findByAgentAndDay(eq(agentId), eq(LocalDate.of(2026, 1, 15)))).thenReturn(List.of());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/collections?date=2026-01-15")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void collectionsForbiddenOutsideOwnBranch() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(UUID.randomUUID());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/collections?date=2026-01-15")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void collectionsUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/admin/agents/" + agentId + "/collections?date=2026-01-15")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
