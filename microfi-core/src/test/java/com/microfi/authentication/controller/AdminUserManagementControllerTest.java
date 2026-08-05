package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminUserManagementController.class)
@Import(SecurityConfig.class)
class AdminUserManagementControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

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

    private Authentication adminAuthentication(AdminRole role, UUID scopedBranchId) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("caller")
                .role(role).branchId(scopedBranchId).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private String createBody(AdminRole role, UUID scopedBranchId) {
        String branchJson = scopedBranchId == null ? "null" : "\"" + scopedBranchId + "\"";
        return "{\"login\":\"newuser\",\"password\":\"password123\",\"role\":\"" + role + "\",\"branchId\":" + branchJson + "}";
    }

    @Test
    void testCreateByAdminSuccess() {
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_MANAGER, branchId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.login").isEqualTo("newuser")
                .jsonPath("$.role").isEqualTo("BRANCH_MANAGER");
    }

    @Test
    void testCreateByManagerOwnBranchCashierSuccess() {
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_CASHIER, branchId))
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void testCreateByManagerWrongRoleForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_MANAGER, branchId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testCreateByManagerDifferentBranchForbidden() {
        UUID otherBranch = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_CASHIER, otherBranch))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testCreateAdminWithBranchIdRejected() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.ADMIN, branchId))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testCreateNonAdminWithoutBranchIdRejected() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_MANAGER, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testCreateDuplicateLoginConflict() {
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_MANAGER, branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testCreateByCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER, branchId)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_CASHIER, branchId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testCreateUnauthenticatedRejected() {
        webTestClient.post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_CASHIER, branchId))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testListAsAdminSeesAllBranches() {
        AdminUser inBranch = AdminUser.builder().id(UUID.randomUUID()).login("a").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        AdminUser otherBranch = AdminUser.builder().id(UUID.randomUUID()).login("b").role(AdminRole.BRANCH_CASHIER).branchId(UUID.randomUUID()).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findAll()).thenReturn(List.of(inBranch, otherBranch));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/users")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(2);
    }

    @Test
    void testListAsManagerSeesOwnBranchOnly() {
        AdminUser inBranch = AdminUser.builder().id(UUID.randomUUID()).login("a").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        AdminUser otherBranch = AdminUser.builder().id(UUID.randomUUID()).login("b").role(AdminRole.BRANCH_CASHIER).branchId(UUID.randomUUID()).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findAll()).thenReturn(List.of(inBranch, otherBranch));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .get()
                .uri("/api/v1/admin/users")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void testUpdateStatusSuccess() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"SUSPENDED\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUSPENDED");
    }

    @Test
    void testUpdateStatusNotFound() {
        UUID id = UUID.randomUUID();
        when(adminUserRepository.findById(id)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"SUSPENDED\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testUpdateStatusOutOfBranchScopeForbidden() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(UUID.randomUUID()).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"SUSPENDED\"}")
                .exchange()
                .expectStatus().isForbidden();
    }
}
