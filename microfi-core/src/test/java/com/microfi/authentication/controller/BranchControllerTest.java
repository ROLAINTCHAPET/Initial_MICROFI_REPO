package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.domain.BranchScheduleDefaults;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.repository.BranchScheduleDefaultsRepository;
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
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = BranchController.class)
@Import(SecurityConfig.class)
class BranchControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private BranchRepository branchRepository;

    @MockitoBean
    private BranchScheduleDefaultsRepository scheduleDefaultsRepository;

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

    private Authentication adminAuthentication(AdminRole role) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void testCreateBranch() {
        when(branchRepository.existsByCode("BR1")).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"BR1\",\"name\":\"Douala Central\",\"timezone\":\"Africa/Douala\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.code").isEqualTo("BR1")
                .jsonPath("$.timezone").isEqualTo("Africa/Douala");
    }

    @Test
    void testCreateBranchWithPhone() {
        when(branchRepository.existsByCode("BR1")).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"BR1\",\"name\":\"Douala Central\",\"phone\":\"+237600000000\",\"timezone\":\"Africa/Douala\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.phone").isEqualTo("+237600000000");
    }

    @Test
    void testCreateBranchDefaultsMaxCashiers() {
        when(branchRepository.existsByCode("BR1")).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"BR1\",\"name\":\"Douala Central\",\"timezone\":\"Africa/Douala\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.maxCashiers").isEqualTo(Branch.DEFAULT_MAX_CASHIERS);
    }

    @Test
    void testCreateBranchDefaultsRequireImeiTrue() {
        when(branchRepository.existsByCode("BR1")).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"BR1\",\"name\":\"Douala Central\",\"timezone\":\"Africa/Douala\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.requireImei").isEqualTo(true);
    }

    @Test
    void testCreateBranchDefaultsCeilingPct100() {
        when(branchRepository.existsByCode("BR1")).thenReturn(false);
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"BR1\",\"name\":\"Douala Central\",\"timezone\":\"Africa/Douala\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.defaultCeilingPct").isEqualTo(Branch.DEFAULT_CEILING_PCT);
    }

    @Test
    void testSetRequireImeiSuccess() {
        UUID id = UUID.randomUUID();
        Branch branch = Branch.builder().id(id).code("BR1").name("Douala Central").timezone("Africa/Douala").build();
        when(branchRepository.findById(id)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/branches/" + id + "/require-imei")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"requireImei\":false}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requireImei").isEqualTo(false);
    }

    @Test
    void testSetRequireImeiCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .patch()
                .uri("/api/v1/admin/branches/" + UUID.randomUUID() + "/require-imei")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"requireImei\":false}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSetMaxCashiersSuccess() {
        UUID id = UUID.randomUUID();
        Branch branch = Branch.builder().id(id).code("BR1").name("Douala Central").timezone("Africa/Douala").build();
        when(branchRepository.findById(id)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/branches/" + id + "/max-cashiers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxCashiers\":5}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.maxCashiers").isEqualTo(5);
    }

    @Test
    void testSetMaxCashiersRejectsBelowOne() {
        UUID id = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/branches/" + id + "/max-cashiers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxCashiers\":0}")
                .exchange()
                .expectStatus().isEqualTo(400);
    }

    @Test
    void testSetMaxCashiersCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .patch()
                .uri("/api/v1/admin/branches/" + UUID.randomUUID() + "/max-cashiers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"maxCashiers\":5}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSetDefaultCeilingPctSuccess() {
        UUID id = UUID.randomUUID();
        Branch branch = Branch.builder().id(id).code("BR1").name("Douala Central").timezone("Africa/Douala").build();
        when(branchRepository.findById(id)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/branches/" + id + "/default-ceiling-pct")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"defaultCeilingPct\":150}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.defaultCeilingPct").isEqualTo(150);
    }

    @Test
    void testSetDefaultCeilingPctRejectsBelowOne() {
        UUID id = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/branches/" + id + "/default-ceiling-pct")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"defaultCeilingPct\":0}")
                .exchange()
                .expectStatus().isEqualTo(400);
    }

    @Test
    void testSetDefaultCeilingPctCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .patch()
                .uri("/api/v1/admin/branches/" + UUID.randomUUID() + "/default-ceiling-pct")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"defaultCeilingPct\":150}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testSetPhoneSuccess() {
        UUID id = UUID.randomUUID();
        Branch branch = Branch.builder().id(id).code("BR1").name("Douala Central").timezone("Africa/Douala").build();
        when(branchRepository.findById(id)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .patch()
                .uri("/api/v1/admin/branches/" + id + "/phone")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phone\":\"+237600000000\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.phone").isEqualTo("+237600000000");
    }

    @Test
    void testSetPhoneCashierForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .patch()
                .uri("/api/v1/admin/branches/" + UUID.randomUUID() + "/phone")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"phone\":\"+237600000000\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testCreateBranchDuplicateCodeConflict() {
        when(branchRepository.existsByCode("BR1")).thenReturn(true);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .post()
                .uri("/api/v1/admin/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"BR1\",\"name\":\"Douala Central\",\"timezone\":\"Africa/Douala\"}")
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void testCreateBranchManagerForbidden() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER)))
                .post()
                .uri("/api/v1/admin/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"BR1\",\"name\":\"Douala Central\",\"timezone\":\"Africa/Douala\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void testListBranches() {
        Branch branch = Branch.builder().id(UUID.randomUUID()).code("BR1").name("Douala Central").timezone("Africa/Douala").build();
        when(branchRepository.findAllByOrderByNameAsc()).thenReturn(List.of(branch));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/branches")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void testPutScheduleSuccess() {
        UUID id = UUID.randomUUID();
        Branch branch = Branch.builder().id(id).code("BR1").name("Douala Central").timezone("Africa/Douala").build();
        when(branchRepository.findById(id)).thenReturn(Optional.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .put()
                .uri("/api/v1/admin/branches/" + id + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"openTime\":\"07:00:00\",\"closeTime\":\"18:00:00\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openTime").isEqualTo("07:00:00")
                .jsonPath("$.closeTime").isEqualTo("18:00:00");
    }

    @Test
    void testPutScheduleRejectsOpenAfterClose() {
        UUID id = UUID.randomUUID();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .put()
                .uri("/api/v1/admin/branches/" + id + "/schedule")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"openTime\":\"18:00:00\",\"closeTime\":\"07:00:00\"}")
                .exchange()
                .expectStatus().isEqualTo(400);
    }

    @Test
    void testGetScheduleNotFound() {
        UUID id = UUID.randomUUID();
        when(branchRepository.findById(id)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/branches/" + id + "/schedule")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testGetScheduleWithConfiguredWindow() {
        UUID id = UUID.randomUUID();
        Branch branch = Branch.builder().id(id).code("BR1").name("Douala Central")
                .openTime(LocalTime.of(7, 0)).closeTime(LocalTime.of(18, 0)).timezone("Africa/Douala").build();
        when(branchRepository.findById(id)).thenReturn(Optional.of(branch));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/branches/" + id + "/schedule")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openTime").isEqualTo("07:00:00");
    }

    @Test
    void testGetScheduleDefaultsFallsBackWhenNeverSet() {
        when(scheduleDefaultsRepository.findById(BranchScheduleDefaults.SINGLETON_ID)).thenReturn(Optional.empty());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .get()
                .uri("/api/v1/admin/branches/schedule-defaults")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openTime").isEqualTo("08:00:00")
                .jsonPath("$.closeTime").isEqualTo("17:00:00");
    }

    @Test
    void testGetScheduleDefaultsReturnsStoredValue() {
        BranchScheduleDefaults defaults = BranchScheduleDefaults.builder().id(BranchScheduleDefaults.SINGLETON_ID)
                .openTime(LocalTime.of(7, 30)).closeTime(LocalTime.of(18, 30)).build();
        when(scheduleDefaultsRepository.findById(BranchScheduleDefaults.SINGLETON_ID)).thenReturn(Optional.of(defaults));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .get()
                .uri("/api/v1/admin/branches/schedule-defaults")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openTime").isEqualTo("07:30:00");
    }

    @Test
    void testPutScheduleDefaultsAppliesOnlyToUnconfiguredBranches() {
        Branch configured = Branch.builder().id(UUID.randomUUID()).code("BR1").name("Douala Central")
                .openTime(LocalTime.of(7, 0)).closeTime(LocalTime.of(18, 0)).build();
        Branch unconfigured = Branch.builder().id(UUID.randomUUID()).code("BR2").name("Yaounde North").build();
        when(scheduleDefaultsRepository.findById(BranchScheduleDefaults.SINGLETON_ID)).thenReturn(Optional.empty());
        when(scheduleDefaultsRepository.save(any(BranchScheduleDefaults.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchRepository.findAll()).thenReturn(List.of(configured, unconfigured));
        when(branchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .put()
                .uri("/api/v1/admin/branches/schedule-defaults")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"openTime\":\"09:00:00\",\"closeTime\":\"16:00:00\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openTime").isEqualTo("09:00:00")
                .jsonPath("$.closeTime").isEqualTo("16:00:00");

        // The already-configured branch keeps its own hours; only the unconfigured one is overwritten.
        org.junit.jupiter.api.Assertions.assertEquals(LocalTime.of(7, 0), configured.getOpenTime());
        org.junit.jupiter.api.Assertions.assertEquals(LocalTime.of(9, 0), unconfigured.getOpenTime());
        org.junit.jupiter.api.Assertions.assertEquals(LocalTime.of(16, 0), unconfigured.getCloseTime());
    }

    @Test
    void testPutScheduleDefaultsOverrideAllAppliesToConfiguredBranchesToo() {
        Branch configured = Branch.builder().id(UUID.randomUUID()).code("BR1").name("Douala Central")
                .openTime(LocalTime.of(7, 0)).closeTime(LocalTime.of(18, 0)).build();
        Branch unconfigured = Branch.builder().id(UUID.randomUUID()).code("BR2").name("Yaounde North").build();
        when(scheduleDefaultsRepository.findById(BranchScheduleDefaults.SINGLETON_ID)).thenReturn(Optional.empty());
        when(scheduleDefaultsRepository.save(any(BranchScheduleDefaults.class))).thenAnswer(inv -> inv.getArgument(0));
        when(branchRepository.findAll()).thenReturn(List.of(configured, unconfigured));
        when(branchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .put()
                .uri("/api/v1/admin/branches/schedule-defaults?overrideAll=true")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"openTime\":\"10:00:00\",\"closeTime\":\"15:00:00\"}")
                .exchange()
                .expectStatus().isOk();

        // overrideAll=true forces every branch, including ones with an existing schedule.
        org.junit.jupiter.api.Assertions.assertEquals(LocalTime.of(10, 0), configured.getOpenTime());
        org.junit.jupiter.api.Assertions.assertEquals(LocalTime.of(15, 0), configured.getCloseTime());
        org.junit.jupiter.api.Assertions.assertEquals(LocalTime.of(10, 0), unconfigured.getOpenTime());
    }

    @Test
    void testPutScheduleDefaultsRejectsOpenAfterClose() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .put()
                .uri("/api/v1/admin/branches/schedule-defaults")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"openTime\":\"18:00:00\",\"closeTime\":\"07:00:00\"}")
                .exchange()
                .expectStatus().isEqualTo(400);
    }

    @Test
    void testPutScheduleDefaultsForbiddenForBranchManager() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER)))
                .put()
                .uri("/api/v1/admin/branches/schedule-defaults")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"openTime\":\"09:00:00\",\"closeTime\":\"16:00:00\"}")
                .exchange()
                .expectStatus().isForbidden();
    }
}
