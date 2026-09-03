package com.microfi.transactions.controller;

import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
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
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.service.SosAlertBroadcaster;
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
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminSosController.class)
@Import(SecurityConfig.class)
class AdminSosControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TrackingService trackingService;

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

    @MockitoBean
    private SosAlertBroadcaster sosAlertBroadcaster;

    @MockitoBean
    private AuditService auditService;

    private final UUID branchId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();

    private Authentication adminAuthentication(AdminRole role, UUID scopedBranchId) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).branchId(scopedBranchId).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void listUnrestrictedForAdmin() {
        when(trackingService.listSosEvents(isNull(), eq(false))).thenReturn(List.of(
                SosResponse.builder().id(UUID.randomUUID()).agentId(agentId).raisedAt(Instant.now()).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/sos-events")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void listScopedToBranchForBranchManager() {
        when(agentDirectoryService.findAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(trackingService.listSosEvents(eq(List.of(agentId)), eq(true))).thenReturn(List.of());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/sos-events?unresolvedOnly=true")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void listUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/admin/sos-events")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void acknowledgeSuccessWithinOwnBranch() {
        UUID sosEventId = UUID.randomUUID();
        when(trackingService.findSosEventAgentId(sosEventId)).thenReturn(agentId);
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(trackingService.acknowledgeSos(eq(sosEventId), any())).thenReturn(
                SosResponse.builder().id(sosEventId).agentId(agentId).raisedAt(Instant.now()).acknowledgedAt(Instant.now()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/sos-events/" + sosEventId + "/acknowledge")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(sosEventId.toString());

        org.mockito.Mockito.verify(auditService).record(org.mockito.ArgumentMatchers.argThat((AuditLogEntry entry) ->
                entry.getEventType().equals("SOS_ACKNOWLEDGED") && entry.getAgentId().equals(agentId) && entry.getBranchId().equals(branchId)));
    }

    @Test
    void streamUnrestrictedForAdminForwardsBroadcastEvents() {
        SosResponse event = SosResponse.builder().id(UUID.randomUUID()).agentId(agentId).raisedAt(Instant.now()).build();
        when(sosAlertBroadcaster.stream()).thenReturn(Flux.just(event));

        FluxExchangeResult<SosResponse> result = webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/sos-events/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SosResponse.class);

        StepVerifier.create(result.getResponseBody())
                .expectNext(event)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void streamOnlyForwardsEventsWithinCallersBranch() {
        UUID otherAgentId = UUID.randomUUID();
        SosResponse inScope = SosResponse.builder().id(UUID.randomUUID()).agentId(agentId).raisedAt(Instant.now()).build();
        SosResponse outOfScope = SosResponse.builder().id(UUID.randomUUID()).agentId(otherAgentId).raisedAt(Instant.now()).build();
        when(agentDirectoryService.findAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(sosAlertBroadcaster.stream()).thenReturn(Flux.just(outOfScope, inScope));

        FluxExchangeResult<SosResponse> result = webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/sos-events/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(SosResponse.class);

        StepVerifier.create(result.getResponseBody())
                .expectNext(inScope)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void streamUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/admin/sos-events/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void acknowledgeOutsideBranchForbidden() {
        UUID sosEventId = UUID.randomUUID();
        when(trackingService.findSosEventAgentId(sosEventId)).thenReturn(agentId);
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(UUID.randomUUID());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/sos-events/" + sosEventId + "/acknowledge")
                .exchange()
                .expectStatus().isForbidden();
    }
}
