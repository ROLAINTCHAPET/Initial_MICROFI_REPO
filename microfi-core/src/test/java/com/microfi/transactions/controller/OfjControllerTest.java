package com.microfi.transactions.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.transactions.service.OfjService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = OfjController.class)
@Import(SecurityConfig.class)
class OfjControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private OfjService ofjService;

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

    private final UUID branchId = UUID.randomUUID();

    private Authentication adminAuthentication(AdminRole role) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void testSummary() {
        when(ofjService.getSummary(branchId)).thenReturn(
                OfjSummaryResponse.builder().sessionId(UUID.randomUUID()).branchId(branchId).status("OPEN").agentLines(List.of()).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/ofj/" + branchId + "/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("OPEN");
    }

    @Test
    void testSummaryUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/ofj/" + branchId + "/summary")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testReconcile() {
        UUID agentId = UUID.randomUUID();
        when(ofjService.reconcile(org.mockito.ArgumentMatchers.eq(branchId), any())).thenReturn(
                OfjAgentLineResponse.builder().id(UUID.randomUUID()).agentId(agentId).digitalTotalXaf(5000).physicalTotalXaf(5000).deltaXaf(0).resolved(true).build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/ofj/" + branchId + "/reconcile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"agentId\":\"" + agentId + "\",\"physicalDenominationLines\":[{\"faceValueXaf\":5000,\"quantity\":1}]}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deltaXaf").isEqualTo(0);
    }

    @Test
    void testReconcileRequiresDenominationLines() {
        UUID agentId = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/ofj/" + branchId + "/reconcile")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"agentId\":\"" + agentId + "\",\"physicalDenominationLines\":[]}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testVariance() {
        UUID lineId = UUID.randomUUID();
        when(ofjService.recordVariance(any())).thenReturn(
                VarianceDebtResponse.builder().id(UUID.randomUUID()).ofjAgentLineId(lineId).amountXaf(1500).status("OPEN").build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/ofj/" + branchId + "/variance")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"ofjAgentLineId\":\"" + lineId + "\",\"comment\":\"short at close\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.amountXaf").isEqualTo(1500);
    }

    @Test
    void testVarianceCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .post()
                .uri("/api/v1/ofj/" + branchId + "/variance")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"ofjAgentLineId\":\"" + UUID.randomUUID() + "\",\"comment\":\"short at close\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testExport() {
        when(ofjService.exportDaily(org.mockito.ArgumentMatchers.eq(branchId), any())).thenReturn(
                com.microfi.shared.dto.ExportBatchResponse.builder().id(UUID.randomUUID()).format("CSV").ackStatus("ACKNOWLEDGED:REF-1").build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/ofj/" + branchId + "/export")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.ackStatus").isEqualTo("ACKNOWLEDGED:REF-1");
    }
}
