package com.microfi.authentication.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.repository.TerminalRepository;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentPasswordResetService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.authentication.service.TerminalService;
import com.microfi.shared.dto.AuthRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AuthenticationController.class)
@Import(SecurityConfig.class)
class AuthenticationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private AgentPasswordResetService agentPasswordResetService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private com.microfi.events.AuthEventPublisher authEventPublisher;

    @MockitoBean
    private BranchRepository branchRepository;

    @MockitoBean
    private TerminalRepository terminalRepository;

    @MockitoBean
    private TerminalService terminalService;

    @Test
    void testLoginSuccess() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(jwtService.generateToken(any(HashMap.class), eq(details))).thenReturn("mock-jwt-token");

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-jwt-token");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.AGENT);
        assertThat(captor.getValue().getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    void testLoginSucceedsForPendingCeilingAgent() {
        // A freshly enrolled agent (escrow not yet funded) can still log in and use the app —
        // they just can't collect deposits yet (see AgentDirectoryServiceTest#verifyTransactionPin*).
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.PENDING_CEILING).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(jwtService.generateToken(any(HashMap.class), eq(details))).thenReturn("mock-jwt-token");

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-jwt-token");
    }

    @Test
    void testLoginInvalidPassword() {
        AuthRequest req = new AuthRequest("agt.dupont", "wrong-password", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches("wrong-password", agent.getPasswordHash())).thenReturn(false);

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginInvalidImei() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "WRONG_IMEI");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginSucceedsWithoutImeiWhenAgentHasNoneOnFile() {
        // Agent enrolled at a branch with IMEI requirement disabled (bring-your-own-phone) —
        // login must succeed regardless of what the app sends for imei, since there's no bound
        // device to check against.
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "SOME-DEVICE-STRING");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei(null).status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(jwtService.generateToken(any(HashMap.class), eq(details))).thenReturn("mock-jwt-token");

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-jwt-token");
    }

    @Test
    void testLoginBindsDeviceOnFirstLoginWhenBranchRequiresImei() {
        // Registration no longer sets an IMEI (AgentManagementController) — the agent's first
        // successful login at a branch that requires device binding is what actually binds it.
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "NEW-DEVICE-IMEI");
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei(null).status(AgentStatus.ACTIVE).branchId(branchId).build();
        AgentDetails details = new AgentDetails(agent);
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1").requireImei(true).build();

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(jwtService.generateToken(any(HashMap.class), eq(details))).thenReturn("mock-jwt-token");

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk();

        assertThat(agent.getImei()).isEqualTo("NEW-DEVICE-IMEI");
        verify(agentDetailsService).bindDevice(agent);
    }

    @Test
    void testLoginRejectsWhenBranchRequiresImeiButNoneWasSent() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "");
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei(null).status(AgentStatus.ACTIVE).branchId(branchId).build();
        AgentDetails details = new AgentDetails(agent);
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1").requireImei(true).build();

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();

        verify(agentDetailsService, org.mockito.Mockito.never()).bindDevice(any());
    }

    @Test
    void testLoginSuspendedAgentBlocked() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.SUSPENDED).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginDeletedAgentBlocked() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.DELETED).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginSucceedsRegardlessOfBranchSchedule() {
        // Login is no longer schedule-gated (moved to collection recording instead — see
        // AgentDirectoryService#requireWithinScheduleWindow / CollectionServiceTest) — an agent
        // can still log in and use the app outside the branch's open/close window, they just can't
        // record a new collection until it reopens.
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE).branchId(branchId).build();
        AgentDetails details = new AgentDetails(agent);
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1")
                .openTime(now.plusHours(2)).closeTime(now.plusHours(3)).timezone(ZoneId.systemDefault().getId()).build();

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));
        when(jwtService.generateToken(any(HashMap.class), eq(details))).thenReturn("mock-jwt-token");

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testLoginLockedAccountRejectedBeforePasswordIsChecked() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE)
                .lockedUntil(Instant.now().plusSeconds(600)).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isEqualTo(423);

        // Locked out means the password is never even checked.
        org.mockito.Mockito.verifyNoInteractions(passwordEncoder);
    }

    @Test
    void testLoginExpiredLockIsIgnored() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE)
                .lockedUntil(Instant.now().minusSeconds(1)).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(jwtService.generateToken(any(HashMap.class), eq(details))).thenReturn("mock-jwt-token");

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testLoginWrongPasswordRegistersFailedAttempt() {
        AuthRequest req = new AuthRequest("agt.dupont", "wrong-password", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches("wrong-password", agent.getPasswordHash())).thenReturn(false);

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();

        verify(agentDetailsService, times(1)).registerFailedLoginAttempt(agent);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.AGENT);
        assertThat(captor.getValue().getStatus()).isEqualTo(AuditStatus.FAILED);
    }

    @Test
    void testLoginSuccessResetsFailedAttempts() {
        AuthRequest req = new AuthRequest("agt.dupont", "password123", "IMEI123");
        Agent agent = Agent.builder().employeeCode("AGT001").username("agt.dupont").imei("IMEI123").status(AgentStatus.ACTIVE).failedPinAttempts(2).build();
        AgentDetails details = new AgentDetails(agent);

        when(agentDetailsService.findByUsername("agt.dupont")).thenReturn(Mono.just(details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(jwtService.generateToken(any(HashMap.class), eq(details))).thenReturn("mock-jwt-token");

        webTestClient.post()
                .uri("/api/v1/auth/agent/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk();

        verify(agentDetailsService, times(1)).resetFailedLoginAttempts(agent);
    }
}
