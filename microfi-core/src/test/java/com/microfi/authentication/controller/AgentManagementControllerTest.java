package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AgentManagementController.class)
@Import(SecurityConfig.class)
class AgentManagementControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentRepository agentRepository;

    @MockitoBean
    private BranchRepository branchRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    // SecurityConfig (imported to exercise the real permitAll chain) transitively needs
    // JwtAuthenticationFilter's dependencies even though this controller doesn't use them.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private EscrowService escrowService;

    private Authentication adminAuthentication(AdminRole role) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private String registerBody(UUID branchId) {
        return "{\"employeeCode\":\"AGT001\",\"fullName\":\"Jean Dupont\",\"phone\":\"+237600000001\"," +
                "\"imei\":\"IMEI-001\",\"pin\":\"1234\",\"branchId\":\"" + branchId + "\"}";
    }

    @Test
    void testRegisterSuccess() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByEmployeeCode("AGT001")).thenReturn(false);
        when(agentRepository.existsByImei("IMEI-001")).thenReturn(false);
        when(branchRepository.existsById(branchId)).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.employeeCode").isEqualTo("AGT001")
                .jsonPath("$.status").isEqualTo("ACTIVE");

        verify(escrowService).createAccountForAgent(any(UUID.class));
    }

    @Test
    void testRegisterDuplicateEmployeeCodeConflict() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByEmployeeCode("AGT001")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testRegisterDuplicateImeiConflict() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByEmployeeCode("AGT001")).thenReturn(false);
        when(agentRepository.existsByImei("IMEI-001")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testRegisterBranchNotFound() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByEmployeeCode("AGT001")).thenReturn(false);
        when(agentRepository.existsByImei("IMEI-001")).thenReturn(false);
        when(branchRepository.existsById(branchId)).thenReturn(false);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testRegisterCashierForbidden() {
        UUID branchId = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testListAgents() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        when(agentRepository.findAll()).thenReturn(List.of(agent));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/agents")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void testSuspendAgent() {
        UUID id = UUID.randomUUID();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"SUSPENDED\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUSPENDED");
    }

    @Test
    void testSuspendAgentNotFound() {
        UUID id = UUID.randomUUID();
        when(agentRepository.findById(id)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"SUSPENDED\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testOverrideCeilingSuccess() {
        UUID id = UUID.randomUUID();
        Instant validUntil = Instant.now().plusSeconds(3600);
        EscrowResponse response = EscrowResponse.builder()
                .agentId(id).balanceXaf(10_000).baseCeilingXaf(10_000).effectiveCeilingXaf(50_000)
                .activeOverrideReason("Market day surge").overrideValidUntil(validUntil).build();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));
        when(escrowService.applyCeilingOverride(eq(id), anyLong(), anyString(), any(Instant.class))).thenReturn(response);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/ceiling")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"tempCeilingXaf\":50000,\"reason\":\"Market day surge\",\"validUntil\":\"" + validUntil + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.effectiveCeilingXaf").isEqualTo(50000);
    }

    @Test
    void testOverrideCeilingRequiresReason() {
        UUID id = UUID.randomUUID();
        Instant validUntil = Instant.now().plusSeconds(3600);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/ceiling")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"tempCeilingXaf\":50000,\"reason\":\"\",\"validUntil\":\"" + validUntil + "\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testOverrideCeilingAgentNotFound() {
        UUID id = UUID.randomUUID();
        Instant validUntil = Instant.now().plusSeconds(3600);
        when(agentRepository.findById(id)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/ceiling")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"tempCeilingXaf\":50000,\"reason\":\"Market day surge\",\"validUntil\":\"" + validUntil + "\"}")
                .exchange()
                .expectStatus().isNotFound();
    }
}
