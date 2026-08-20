package com.microfi.authentication.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.repository.AdminUserRepository;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.savings.service.ClientDetailsService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminUserSelfController.class)
@Import(SecurityConfig.class)
class AdminUserSelfControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    private final UUID branchId = UUID.randomUUID();

    private AdminUser buildCaller() {
        return AdminUser.builder().id(UUID.randomUUID()).login("cashier1")
                .fullName("Cashier One").phone("+237600000020")
                .role(AdminRole.BRANCH_CASHIER).branchId(branchId).status(AdminUserStatus.ACTIVE)
                .passwordHash("hashed-old").mustChangePassword(true).build();
    }

    private Authentication callerAuthentication(AdminUser caller) {
        AdminUserDetails details = new AdminUserDetails(caller);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private Authentication agentAuthentication() {
        Agent agent = Agent.builder().id(UUID.randomUUID()).employeeCode("AGT001").username("agt.test").status(AgentStatus.ACTIVE).build();
        AgentDetails details = new AgentDetails(agent);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void meReturnsTheCallingUsersOwnProfile() {
        AdminUser caller = buildCaller();

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(callerAuthentication(caller)))
                .get()
                .uri("/api/v1/admin/users/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.login").isEqualTo("cashier1")
                .jsonPath("$.fullName").isEqualTo("Cashier One")
                .jsonPath("$.mustChangePassword").isEqualTo(true);
    }

    @Test
    void meRejectsNonAdminUserPrincipals() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(agentAuthentication()))
                .get()
                .uri("/api/v1/admin/users/me")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void changePasswordSuccessClearsMustChangeFlagAndReturnsToken() {
        AdminUser caller = buildCaller();
        when(passwordEncoder.matches(eq("oldpass123"), eq("hashed-old"))).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-new");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(), any())).thenReturn("fresh-jwt-token");

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(callerAuthentication(caller)))
                .patch()
                .uri("/api/v1/admin/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\":\"oldpass123\",\"newPassword\":\"newpassword456\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("fresh-jwt-token");

        org.mockito.Mockito.verify(adminUserRepository).save(
                org.mockito.ArgumentMatchers.argThat(u -> !Boolean.TRUE.equals(u.getMustChangePassword()) && "hashed-new".equals(u.getPasswordHash())));
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        AdminUser caller = buildCaller();
        when(passwordEncoder.matches(eq("wrong"), eq("hashed-old"))).thenReturn(false);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(callerAuthentication(caller)))
                .patch()
                .uri("/api/v1/admin/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"currentPassword\":\"wrong\",\"newPassword\":\"newpassword456\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
