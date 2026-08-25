package com.microfi.authentication.controller;

import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.authentication.service.AgentSelfService;
import com.microfi.authentication.service.JwtService;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.notifications.service.NotificationService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.shared.dto.RouteResponse;
import com.microfi.transactions.service.TrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AgentSelfController.class)
@Import(SecurityConfig.class)
class AgentSelfControllerTest {

    @Autowired
    private WebTestClient webTestClient;

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
    private TrackingService trackingService;

    @MockitoBean
    private BranchRepository branchRepository;

    @MockitoBean
    private AgentSelfService agentSelfService;

    @MockitoBean
    private AgentDirectoryService agentDirectoryService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private MfiSettingsService mfiSettingsService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    private Agent buildAgent() {
        return Agent.builder()
                .id(agentId)
                .employeeCode("AGT-DEMO01")
                .username("agt.demo01")
                .email("agt.demo01@microfi.test")
                .fullName("Demo Collections Agent")
                .phone("+237600000199")
                .imei("IMEI-DEMO01")
                .branchId(branchId)
                .status(AgentStatus.ACTIVE)
                .pinHash("hashed-pin")
                .pinMustChange(false)
                .build();
    }

    private Authentication agentAuthentication() {
        AgentDetails details = new AgentDetails(buildAgent());
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private Authentication adminAuthentication() {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(AdminRole.ADMIN).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void meReturnsTheCallingAgentsOwnProfile() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .get()
                .uri("/api/v1/agents/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(agentId.toString())
                .jsonPath("$.employeeCode").isEqualTo("AGT-DEMO01")
                .jsonPath("$.branchId").isEqualTo(branchId.toString())
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void meRejectsUnauthenticatedCallers() {
        webTestClient.get()
                .uri("/api/v1/agents/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void meRejectsNonAgentPrincipals() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication()))
                .get()
                .uri("/api/v1/agents/me")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void myRouteReturnsTheCallingAgentsOwnRouteForGivenDate() {
        LocalDate date = LocalDate.of(2026, 8, 11);
        RouteResponse response = RouteResponse.builder().agentId(agentId).date(date).points(List.of()).transactions(List.of()).build();
        when(trackingService.getRoute(eq(agentId), eq(date))).thenReturn(response);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .get()
                .uri("/api/v1/agents/me/route?date=" + date)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.agentId").isEqualTo(agentId.toString());
    }

    @Test
    void myRouteDefaultsToTodayWhenNoDateGiven() {
        when(trackingService.getRoute(eq(agentId), any())).thenReturn(RouteResponse.builder().agentId(agentId).points(List.of()).transactions(List.of()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .get()
                .uri("/api/v1/agents/me/route")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void myRouteRejectsNonAgentPrincipals() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication()))
                .get()
                .uri("/api/v1/agents/me/route")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void mySosReturnsTheCallingAgentsOwnAlertsIncludingAcknowledgement() {
        java.time.Instant raisedAt = java.time.Instant.now();
        java.time.Instant ackAt = raisedAt.plusSeconds(300);
        com.microfi.shared.dto.SosResponse response = com.microfi.shared.dto.SosResponse.builder()
                .id(java.util.UUID.randomUUID()).agentId(agentId).raisedAt(raisedAt)
                .acknowledgedBy(java.util.UUID.randomUUID()).acknowledgedAt(ackAt).build();
        when(trackingService.listSosEvents(List.of(agentId), false)).thenReturn(List.of(response));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .get()
                .uri("/api/v1/agents/me/sos")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void mySosRejectsNonAgentPrincipals() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication()))
                .get()
                .uri("/api/v1/agents/me/sos")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void myBranchReturnsTheCallingAgentsOwnBranch() {
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Douala Central").phone("+237600000000").timezone("Africa/Douala").build();
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .get()
                .uri("/api/v1/agents/me/branch")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Douala Central")
                .jsonPath("$.phone").isEqualTo("+237600000000");
    }

    @Test
    void myBranchRejectsNonAgentPrincipals() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication()))
                .get()
                .uri("/api/v1/agents/me/branch")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void changePinSuccess() {
        Agent saved = buildAgent();
        saved.setPinMustChange(false);
        when(agentSelfService.changePin(any(Agent.class), any())).thenReturn(saved);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .patch()
                .uri("/api/v1/agents/me/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPin\":\"0000\",\"newPin\":\"7392\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.pinMustChange").isEqualTo(false);
    }

    @Test
    void changePinRejectsWrongCurrentPin() {
        when(agentSelfService.changePin(any(Agent.class), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current PIN is incorrect"));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .patch()
                .uri("/api/v1/agents/me/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPin\":\"0000\",\"newPin\":\"7392\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void changePinRejectsWeakNewPin() {
        when(agentSelfService.changePin(any(Agent.class), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "PIN must not be all the same digit or a simple sequence (e.g. 1234)"));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .patch()
                .uri("/api/v1/agents/me/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPin\":\"0000\",\"newPin\":\"1234\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void changePinRejectsNonAgentPrincipals() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication()))
                .patch()
                .uri("/api/v1/agents/me/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPin\":\"0000\",\"newPin\":\"7392\"}")
                .exchange()
                .expectStatus().isForbidden();
    }
}
