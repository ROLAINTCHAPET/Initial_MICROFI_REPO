package com.microfi.authentication.controller;

import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.savings.service.ClientDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.shared.dto.AdminLoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AdminAuthenticationController.class)
@Import(SecurityConfig.class)
class AdminAuthenticationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    // SecurityConfig (imported to exercise the real permitAll chain for /api/v1/auth/**)
    // transitively needs JwtAuthenticationFilter's dependencies even though this controller
    // doesn't use them.
    @MockitoBean
    private AgentDetailsService agentDetailsService;

    private AdminUser adminUser(AdminUserStatus status) {
        return AdminUser.builder().id(UUID.randomUUID()).login("admin").passwordHash("hashed")
                .role(AdminRole.ADMIN).status(status).build();
    }

    @Test
    void testLoginSuccess() {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setLogin("admin");
        req.setPassword("ChangeMe123!");
        com.microfi.authentication.AdminUserDetails details = new com.microfi.authentication.AdminUserDetails(adminUser(AdminUserStatus.ACTIVE));

        when(adminUserDetailsService.findByUsername("admin")).thenReturn(Mono.just((UserDetails) details));
        when(passwordEncoder.matches("ChangeMe123!", details.getPassword())).thenReturn(true);
        when(jwtService.generateToken(any(), any())).thenReturn("mock-admin-jwt");

        webTestClient.post()
                .uri("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mock-admin-jwt");
    }

    @Test
    void testLoginWrongPasswordRejected() {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setLogin("admin");
        req.setPassword("wrong");
        com.microfi.authentication.AdminUserDetails details = new com.microfi.authentication.AdminUserDetails(adminUser(AdminUserStatus.ACTIVE));

        when(adminUserDetailsService.findByUsername("admin")).thenReturn(Mono.just((UserDetails) details));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

        webTestClient.post()
                .uri("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginSuspendedAdminRejected() {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setLogin("admin");
        req.setPassword("ChangeMe123!");
        com.microfi.authentication.AdminUserDetails details = new com.microfi.authentication.AdminUserDetails(adminUser(AdminUserStatus.SUSPENDED));

        when(adminUserDetailsService.findByUsername("admin")).thenReturn(Mono.just((UserDetails) details));

        webTestClient.post()
                .uri("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginDeletedAdminRejected() {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setLogin("admin");
        req.setPassword("ChangeMe123!");
        com.microfi.authentication.AdminUserDetails details = new com.microfi.authentication.AdminUserDetails(adminUser(AdminUserStatus.DELETED));

        when(adminUserDetailsService.findByUsername("admin")).thenReturn(Mono.just((UserDetails) details));

        webTestClient.post()
                .uri("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void testLoginUnknownUserRejected() {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setLogin("ghost");
        req.setPassword("whatever1");

        when(adminUserDetailsService.findByUsername("ghost"))
                .thenReturn(Mono.error(new UsernameNotFoundException("Admin user not found: ghost")));

        webTestClient.post()
                .uri("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
