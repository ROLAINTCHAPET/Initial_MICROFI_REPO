package com.microfi.registration.controller;

import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.registration.domain.RegistrationApplication;
import com.microfi.registration.domain.RegistrationApplicationStatus;
import com.microfi.registration.domain.RegistrationTargetRole;
import com.microfi.registration.service.DocumentStorageService;
import com.microfi.registration.service.RegistrationApplicationService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.JwtService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = RegistrationApplicationController.class)
@Import(SecurityConfig.class)
class RegistrationApplicationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private RegistrationApplicationService registrationApplicationService;

    @MockitoBean
    private DocumentStorageService documentStorageService;

    @MockitoBean
    private AuditService auditService;

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
    private final UUID otherBranchId = UUID.randomUUID();

    private Authentication adminAuthentication(AdminRole role, UUID callerBranchId) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).branchId(callerBranchId).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private RegistrationApplication application(UUID id, RegistrationTargetRole targetRole, UUID appBranchId) {
        return RegistrationApplication.builder().id(id).targetRole(targetRole).branchId(appBranchId)
                .firstName("Jean").lastName("Agent").status(RegistrationApplicationStatus.SUBMITTED).build();
    }

    @Test
    void branchManagerApprovesOwnBranchAgentApplication() {
        UUID id = UUID.randomUUID();
        RegistrationApplication app = application(id, RegistrationTargetRole.AGENT, branchId);
        when(registrationApplicationService.get(id)).thenReturn(app);
        when(registrationApplicationService.approve(eq(id), any(), any()))
                .thenReturn(Mono.just(new RegistrationApplicationService.ApprovalResult(app, "TempPass1", "1234")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/registration-applications/" + id + "/approve")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void branchManagerRejectsOwnBranchCashierApplication() {
        UUID id = UUID.randomUUID();
        RegistrationApplication app = application(id, RegistrationTargetRole.BRANCH_CASHIER, branchId);
        when(registrationApplicationService.get(id)).thenReturn(app);
        when(registrationApplicationService.reject(any(), any(), any())).thenReturn(app);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/registration-applications/" + id + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Incomplete documents\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void branchManagerCannotApproveAnotherBranchsApplication() {
        UUID id = UUID.randomUUID();
        when(registrationApplicationService.get(id)).thenReturn(application(id, RegistrationTargetRole.AGENT, otherBranchId));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/registration-applications/" + id + "/approve")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void branchManagerCannotApproveABranchManagerApplication() {
        UUID id = UUID.randomUUID();
        when(registrationApplicationService.get(id)).thenReturn(application(id, RegistrationTargetRole.BRANCH_MANAGER, branchId));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/registration-applications/" + id + "/approve")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void branchManagerCannotRejectABranchManagerApplication() {
        UUID id = UUID.randomUUID();
        when(registrationApplicationService.get(id)).thenReturn(application(id, RegistrationTargetRole.BRANCH_MANAGER, branchId));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/registration-applications/" + id + "/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Not for me to decide\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void adminApprovesABranchManagerApplication() {
        UUID id = UUID.randomUUID();
        RegistrationApplication app = application(id, RegistrationTargetRole.BRANCH_MANAGER, branchId);
        when(registrationApplicationService.get(id)).thenReturn(app);
        when(registrationApplicationService.approve(eq(id), any(), any()))
                .thenReturn(Mono.just(new RegistrationApplicationService.ApprovalResult(app, "TempPass1", "1234")));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/registration-applications/" + id + "/approve")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void cashierForbiddenFromApproving() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER, branchId)))
                .patch()
                .uri("/api/v1/admin/registration-applications/" + UUID.randomUUID() + "/approve")
                .exchange()
                .expectStatus().isForbidden();
    }
}
