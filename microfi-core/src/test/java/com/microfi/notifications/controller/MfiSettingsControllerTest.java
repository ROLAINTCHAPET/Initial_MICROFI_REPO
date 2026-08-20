package com.microfi.notifications.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.SecurityConfig;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import com.microfi.authentication.service.AdminUserDetailsService;
import com.microfi.authentication.service.AgentDetailsService;
import com.microfi.authentication.service.JwtService;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.savings.service.ClientDetailsService;
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

import java.util.UUID;

import static org.mockito.Mockito.when;

@WebFluxTest(controllers = MfiSettingsController.class)
@Import(SecurityConfig.class)
class MfiSettingsControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private MfiSettingsService mfiSettingsService;

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
    void getIsAllowedForAnyBackOfficeRole() {
        when(mfiSettingsService.getName()).thenReturn("Coopec Alpha");

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_CASHIER)))
                .get()
                .uri("/api/v1/admin/settings/mfi")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Coopec Alpha");
    }

    @Test
    void updateSucceedsForAdmin() {
        when(mfiSettingsService.updateName("Coopec Beta")).thenReturn("Coopec Beta");

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN)))
                .put()
                .uri("/api/v1/admin/settings/mfi")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Coopec Beta\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Coopec Beta");
    }

    @Test
    void updateRejectedForNonAdmin() {
        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER)))
                .put()
                .uri("/api/v1/admin/settings/mfi")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Coopec Beta\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/admin/settings/mfi")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
