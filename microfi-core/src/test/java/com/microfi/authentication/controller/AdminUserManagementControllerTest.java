package com.microfi.authentication.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AdminUserEnrollmentService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminUserManagementController.class)
@Import({SecurityConfig.class, AdminUserEnrollmentService.class})
class AdminUserManagementControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private BranchRepository branchRepository;

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
        return "{\"login\":\"newuser\",\"password\":\"password123\",\"fullName\":\"New User\",\"phone\":\"+237600000010\"," +
                "\"role\":\"" + role + "\",\"branchId\":" + branchJson + "}";
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
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(Branch.builder().id(branchId).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_CASHIER, branchId))
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void testCreateManagerConflictsWithExistingManager() {
        AdminUser existingManager = AdminUser.builder().id(UUID.randomUUID()).login("current-mgr")
                .role(AdminRole.BRANCH_MANAGER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(false);
        when(adminUserRepository.findByBranchIdAndRole(branchId, AdminRole.BRANCH_MANAGER)).thenReturn(List.of(existingManager));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_MANAGER, branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testCreateManagerReplaceConfirmedSuspendsOldOneAndCreatesNew() {
        AdminUser existingManager = AdminUser.builder().id(UUID.randomUUID()).login("current-mgr")
                .role(AdminRole.BRANCH_MANAGER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(false);
        when(adminUserRepository.findByBranchIdAndRole(branchId, AdminRole.BRANCH_MANAGER)).thenReturn(List.of(existingManager));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = "{\"login\":\"newuser\",\"password\":\"password123\",\"fullName\":\"New User\",\"phone\":\"+237600000010\"," +
                "\"role\":\"BRANCH_MANAGER\",\"branchId\":\"" + branchId + "\",\"replaceUserId\":\"" + existingManager.getId() + "\"}";

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();

        org.mockito.Mockito.verify(adminUserRepository).save(
                org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(existingManager.getId()) && u.getStatus() == AdminUserStatus.SUSPENDED));
    }

    @Test
    void testCreateCashierAtCapConflictsWithoutReplaceUserId() {
        AdminUser existingCashier = AdminUser.builder().id(UUID.randomUUID()).login("c1")
                .role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(false);
        when(adminUserRepository.findByBranchIdAndRole(branchId, AdminRole.BRANCH_CASHIER)).thenReturn(List.of(existingCashier));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(Branch.builder().id(branchId).maxCashiers(1).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_CASHIER, branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testCreateCashierReplaceConfirmedSuspendsOldOneAndCreatesNew() {
        AdminUser existingCashier = AdminUser.builder().id(UUID.randomUUID()).login("c1")
                .role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(false);
        when(adminUserRepository.findByBranchIdAndRole(branchId, AdminRole.BRANCH_CASHIER)).thenReturn(List.of(existingCashier));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(Branch.builder().id(branchId).maxCashiers(1).build()));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        String body = "{\"login\":\"newuser\",\"password\":\"password123\",\"fullName\":\"New User\",\"phone\":\"+237600000010\"," +
                "\"role\":\"BRANCH_CASHIER\",\"branchId\":\"" + branchId + "\",\"replaceUserId\":\"" + existingCashier.getId() + "\"}";

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();

        org.mockito.Mockito.verify(adminUserRepository).save(
                org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(existingCashier.getId()) && u.getStatus() == AdminUserStatus.SUSPENDED));
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
    void testCreateDuplicatePhoneConflict() {
        when(adminUserRepository.existsByLogin("newuser")).thenReturn(false);
        when(adminUserRepository.existsByPhone("+237600000010")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .post()
                .uri("/api/v1/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(AdminRole.BRANCH_MANAGER, branchId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testCreateSetsMustChangePasswordTrue() {
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
                .jsonPath("$.mustChangePassword").isEqualTo(true)
                .jsonPath("$.fullName").isEqualTo("New User")
                .jsonPath("$.phone").isEqualTo("+237600000010");
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

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getActorType()).isEqualTo(AuditActorType.ADMIN);
        assertThat(captor.getValue().getTargetAdminUserId()).isEqualTo(id);
        assertThat(captor.getValue().getEventType()).isEqualTo("ADMIN_USER_SUSPENDED");
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

    @Test
    void testGetSuccess() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/users/" + id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.login").isEqualTo("target");
    }

    @Test
    void testUpdateRoleByAdminSuccess() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"role\":\"BRANCH_MANAGER\",\"branchId\":\"" + branchId + "\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.role").isEqualTo("BRANCH_MANAGER");
    }

    @Test
    void testUpdateRoleByManagerForbidden() {
        UUID id = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"role\":\"BRANCH_MANAGER\",\"branchId\":\"" + branchId + "\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testUpdateRoleInconsistentBranchRejected() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/role")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"role\":\"ADMIN\",\"branchId\":\"" + branchId + "\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testResetPasswordSuccess() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode(anyString())).thenReturn("newhash");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"newPassword\":\"newpassword123\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testResetPasswordByCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER, branchId)))
                .patch()
                .uri("/api/v1/admin/users/" + UUID.randomUUID() + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"newPassword\":\"newpassword123\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testDeleteByAdminSuccess() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_MANAGER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"No longer employed by the MFI\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DELETED")
                .jsonPath("$.deletionReason").isEqualTo("No longer employed by the MFI");
    }

    @Test
    void testDeleteByManagerOwnBranchCashierSuccess() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Left the branch\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DELETED");
    }

    @Test
    void testDeleteByManagerOwnBranchPeerManagerSuccess() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("peer-mgr").role(AdminRole.BRANCH_MANAGER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Role consolidated\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void testDeleteByManagerOfAdminForbidden() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("root-admin").role(AdminRole.ADMIN).branchId(null).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"attempt\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testDeleteByManagerOutOfBranchScopeForbidden() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(UUID.randomUUID()).status(AdminUserStatus.ACTIVE).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"attempt\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testDeleteOwnAccountRejected() {
        UUID callerId = UUID.randomUUID();
        AdminUser caller = AdminUser.builder().id(callerId).login("caller").role(AdminRole.ADMIN).branchId(null).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(caller);
        Authentication authentication = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        when(adminUserRepository.findById(callerId)).thenReturn(Optional.of(caller));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(authentication))
                .patch()
                .uri("/api/v1/admin/users/" + callerId + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"attempt\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testDeleteAlreadyDeletedConflict() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.DELETED).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"attempt\"}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testDeleteBlankReasonRejected() {
        UUID id = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testUpdateStatusOnDeletedAccountConflict() {
        UUID id = UUID.randomUUID();
        AdminUser target = AdminUser.builder().id(id).login("target").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.DELETED).build();
        when(adminUserRepository.findById(id)).thenReturn(Optional.of(target));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACTIVE\"}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testUpdateStatusToDeletedRejected() {
        UUID id = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/users/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"DELETED\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testListExcludesDeletedAccounts() {
        AdminUser active = AdminUser.builder().id(UUID.randomUUID()).login("a").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        AdminUser deleted = AdminUser.builder().id(UUID.randomUUID()).login("b").role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.DELETED).build();
        when(adminUserRepository.findAll()).thenReturn(List.of(active, deleted));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/users")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }
}
