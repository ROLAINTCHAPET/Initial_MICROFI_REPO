package com.microfi.authentication;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.domain.AdminUser;
import com.microfi.authentication.domain.AdminUserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminAccessTest {

    private AdminUserDetails adminDetails(AdminRole role, UUID branchId) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).branchId(branchId).status(AdminUserStatus.ACTIVE).build();
        return new AdminUserDetails(adminUser);
    }

    private Mono<Authentication> authOf(AdminUserDetails details) {
        return Mono.just(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @Test
    void requireAllowsMatchingRole() {
        AdminUserDetails details = adminDetails(AdminRole.BRANCH_MANAGER, UUID.randomUUID());

        StepVerifier.create(AdminAccess.require(authOf(details), AdminRole.ADMIN, AdminRole.BRANCH_MANAGER))
                .expectNext(details)
                .verifyComplete();
    }

    @Test
    void requireAllowsAnyRoleWhenNoneSpecified() {
        AdminUserDetails details = adminDetails(AdminRole.BRANCH_CASHIER, UUID.randomUUID());

        StepVerifier.create(AdminAccess.require(authOf(details)))
                .expectNext(details)
                .verifyComplete();
    }

    @Test
    void requireRejectsNonMatchingRole() {
        AdminUserDetails details = adminDetails(AdminRole.BRANCH_CASHIER, UUID.randomUUID());

        StepVerifier.create(AdminAccess.require(authOf(details), AdminRole.ADMIN))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse && rse.getStatusCode().value() == 403)
                .verify();
    }

    @Test
    void requireRejectsNonAdminPrincipal() {
        Agent agent = Agent.builder().employeeCode("AGT001").status(AgentStatus.ACTIVE).build();
        AgentDetails agentDetails = new AgentDetails(agent);
        Mono<Authentication> auth = Mono.just(new UsernamePasswordAuthenticationToken(agentDetails, null, agentDetails.getAuthorities()));

        StepVerifier.create(AdminAccess.require(auth))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse && rse.getStatusCode().value() == 403)
                .verify();
    }

    @Test
    void requireBranchScopeAdminBypassesAnyBranch() {
        AdminUserDetails details = adminDetails(AdminRole.ADMIN, null);

        AdminAccess.requireBranchScope(details, UUID.randomUUID());
    }

    @Test
    void requireBranchScopeManagerWithinOwnBranchPasses() {
        UUID branchId = UUID.randomUUID();
        AdminUserDetails details = adminDetails(AdminRole.BRANCH_MANAGER, branchId);

        AdminAccess.requireBranchScope(details, branchId);
    }

    @Test
    void requireBranchScopeManagerOutsideOwnBranchRejected() {
        AdminUserDetails details = adminDetails(AdminRole.BRANCH_MANAGER, UUID.randomUUID());

        assertThatThrownBy(() -> AdminAccess.requireBranchScope(details, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(403));
    }
}
