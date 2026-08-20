package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentEnrollmentService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.transactions.service.EscrowService;
import com.microfi.transactions.service.OfjService;
import com.microfi.shared.dto.EscrowResponse;
import com.microfi.shared.dto.VarianceDebtResponse;
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
@Import({SecurityConfig.class, AgentEnrollmentService.class})
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
    private ClientDetailsService clientDetailsService;

    @MockitoBean
    private EscrowService escrowService;

    @MockitoBean
    private OfjService ofjService;

    private Authentication adminAuthentication(AdminRole role) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private String registerBody(UUID branchId) {
        return "{\"employeeCode\":\"AGT001\",\"fullName\":\"Jean Dupont\",\"phone\":\"+237600000001\"," +
                "\"imei\":\"IMEI-001\",\"username\":\"agt.dupont\",\"email\":\"agt.dupont@microfi.test\"," +
                "\"password\":\"password123\",\"pin\":\"1234\",\"branchId\":\"" + branchId + "\"}";
    }

    @Test
    void testRegisterSuccess() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByEmployeeCode("AGT001")).thenReturn(false);
        when(agentRepository.existsByUsername("agt.dupont")).thenReturn(false);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(Branch.builder().id(branchId).build()));
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
                .jsonPath("$.status").isEqualTo("PENDING_CEILING");

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
    void testRegisterDuplicateUsernameConflict() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByEmployeeCode("AGT001")).thenReturn(false);
        when(agentRepository.existsByUsername("agt.dupont")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testRegisterDuplicatePhoneConflict() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByUsername("agt.dupont")).thenReturn(false);
        when(agentRepository.existsByPhone("+237600000001")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testRegisterDuplicateEmailConflict() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByUsername("agt.dupont")).thenReturn(false);
        when(agentRepository.existsByPhone("+237600000001")).thenReturn(false);
        when(agentRepository.existsByEmail("agt.dupont@microfi.test")).thenReturn(true);

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
        when(branchRepository.findById(branchId)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody(branchId))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testRegisterNeverSetsImeiEvenWhenBranchRequiresBinding() {
        // Device binding now happens on the agent's first login, not at registration — even a
        // branch with requireImei=true gets an unbound agent here.
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByUsername("agt.dupont")).thenReturn(false);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(Branch.builder().id(branchId).requireImei(true).build()));
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
                .jsonPath("$.imei").isEmpty();

        verify(agentRepository, org.mockito.Mockito.never()).existsByImei(any());
    }

    @Test
    void testRegisterDefaultsEmployeeCodeToUsernameWhenOmitted() {
        UUID branchId = UUID.randomUUID();
        when(agentRepository.existsByUsername("agt.dupont")).thenReturn(false);
        when(agentRepository.existsByEmployeeCode("agt.dupont")).thenReturn(false);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(Branch.builder().id(branchId).build()));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = "{\"fullName\":\"Jean Dupont\",\"phone\":\"+237600000002\"," +
                "\"username\":\"agt.dupont\",\"email\":\"agt.dupont@microfi.test\",\"password\":\"password123\"," +
                "\"pin\":\"1234\",\"branchId\":\"" + branchId + "\"}";

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.employeeCode").isEqualTo("agt.dupont");
    }

    @Test
    void testRegisterRejectsNonInternationalPhoneFormat() {
        UUID branchId = UUID.randomUUID();
        String body = "{\"fullName\":\"Jean Dupont\",\"phone\":\"0600000001\"," +
                "\"username\":\"agt.dupont\",\"email\":\"agt.dupont@microfi.test\",\"password\":\"password123\"," +
                "\"pin\":\"1234\",\"branchId\":\"" + branchId + "\"}";

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
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
    void testReactivateAgentWithConfiguredCeilingSucceeds() {
        UUID id = UUID.randomUUID();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").status(AgentStatus.SUSPENDED).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(escrowService.getStatus(id)).thenReturn(EscrowResponse.builder().agentId(id).baseCeilingXaf(50_000).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACTIVE\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE");
    }

    @Test
    void testReactivateAgentWithoutConfiguredCeilingRejected() {
        UUID id = UUID.randomUUID();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").status(AgentStatus.SUSPENDED).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));
        when(escrowService.getStatus(id)).thenReturn(EscrowResponse.builder().agentId(id).baseCeilingXaf(0).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACTIVE\"}")
                .exchange()
                .expectStatus().isEqualTo(409);

        verify(agentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void testReactivateAgentFromPendingCeilingRejected() {
        UUID id = UUID.randomUUID();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").status(AgentStatus.PENDING_CEILING).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACTIVE\"}")
                .exchange()
                .expectStatus().isEqualTo(409);

        verify(agentRepository, org.mockito.Mockito.never()).save(any());
        verify(escrowService, org.mockito.Mockito.never()).getStatus(any());
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
    void testResetDeviceBindingClearsImeiAndRecordsReason() {
        UUID id = UUID.randomUUID();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").imei("OLD-DEVICE").status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/device-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Lost phone, reported at branch\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.imei").isEmpty()
                .jsonPath("$.deviceResetReason").isEqualTo("Lost phone, reported at branch");

        org.mockito.Mockito.verify(agentRepository).save(org.mockito.ArgumentMatchers.argThat(
                a -> a.getImei() == null && "Lost phone, reported at branch".equals(a.getDeviceResetReason()) && a.getDeviceResetAt() != null));
    }

    @Test
    void testResetDeviceBindingRequiresReason() {
        UUID id = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/device-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testResetDeviceBindingNotFound() {
        UUID id = UUID.randomUUID();
        when(agentRepository.findById(id)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/agents/" + id + "/device-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Lost phone\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testResetDeviceBindingByCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .patch()
                .uri("/api/v1/admin/agents/" + UUID.randomUUID() + "/device-binding")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Lost phone\"}")
                .exchange()
                .expectStatus().isForbidden();
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

    @Test
    void testGetAgentSuccess() {
        UUID id = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").branchId(branchId).status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/agents/" + id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.employeeCode").isEqualTo("AGT001");
    }

    @Test
    void testGetAgentNotFound() {
        UUID id = UUID.randomUUID();
        when(agentRepository.findById(id)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/agents/" + id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testAgentVarianceDebts() {
        UUID id = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(id).employeeCode("AGT001").branchId(branchId).status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));
        when(ofjService.listVarianceDebtsForAgent(id, false)).thenReturn(List.of(
                VarianceDebtResponse.builder().id(UUID.randomUUID()).agentId(id).amountXaf(2000).status("OPEN").build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/agents/" + id + "/variance-debts")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }
}
