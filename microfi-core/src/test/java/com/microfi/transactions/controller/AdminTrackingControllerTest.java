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
import com.microfi.shared.dto.GeofenceAlertResponse;
import com.microfi.shared.dto.GeofenceResponse;
import com.microfi.shared.dto.RouteResponse;
import com.microfi.transactions.service.GeofenceService;
import com.microfi.transactions.service.TrackingService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminTrackingController.class)
@Import(SecurityConfig.class)
class AdminTrackingControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TrackingService trackingService;

    @MockitoBean
    private GeofenceService geofenceService;

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
    void routeSucceedsForOwnBranchManager() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(trackingService.getRoute(eq(agentId), eq(LocalDate.of(2026, 1, 15)))).thenReturn(
                RouteResponse.builder().agentId(agentId).date(LocalDate.of(2026, 1, 15)).points(List.of()).transactions(List.of()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/route?date=2026-01-15")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agentId").isEqualTo(agentId.toString());
    }

    @Test
    void routeForbiddenOutsideOwnBranch() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(UUID.randomUUID());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/route?date=2026-01-15")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void setGeofenceSucceedsForAdmin() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(geofenceService.setGeofence(eq(agentId), org.mockito.ArgumentMatchers.any())).thenReturn(
                GeofenceResponse.builder().agentId(agentId).vertices(List.of()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .put()
                .uri("/api/v1/admin/agents/" + agentId + "/geofence")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"vertices\":[{\"lat\":0,\"lon\":0},{\"lat\":0,\"lon\":10},{\"lat\":10,\"lon\":10}]}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void geofenceAlertsListsForOwnBranch() {
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(geofenceService.listAlerts(agentId)).thenReturn(List.of(
                GeofenceAlertResponse.builder().id(UUID.randomUUID()).agentId(agentId).active(true).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/agents/" + agentId + "/geofence-alerts")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void routeUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/admin/agents/" + agentId + "/route?date=2026-01-15")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
