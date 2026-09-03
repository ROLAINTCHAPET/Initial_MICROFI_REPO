package com.microfi.transactions.controller;

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
import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import com.microfi.transactions.service.CollectionRejectionProofStorageService;
import com.microfi.transactions.service.CollectionRejectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = CollectionRejectionController.class)
@Import(SecurityConfig.class)
class CollectionRejectionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CollectionRejectionService collectionRejectionService;

    @MockitoBean
    private CollectionRejectionProofStorageService collectionRejectionProofStorageService;

    @MockitoBean
    private AgentDirectoryService agentDirectoryService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AgentDetailsService agentDetailsService;

    @MockitoBean
    private AdminUserDetailsService adminUserDetailsService;

    @MockitoBean
    private ClientDetailsService clientDetailsService;

    private final UUID branchId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();

    private Authentication adminAuthentication(AdminRole role, UUID scopedBranchId) {
        AdminUser adminUser = AdminUser.builder().id(UUID.randomUUID()).login("admin")
                .role(role).branchId(scopedBranchId).status(AdminUserStatus.ACTIVE).build();
        AdminUserDetails details = new AdminUserDetails(adminUser);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    private org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> proofOnlyBody() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("proof", new ByteArrayResource("fake-proof".getBytes()) {
            @Override
            public String getFilename() {
                return "proof.jpg";
            }
        }).header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE);
        return builder.build();
    }

    @Test
    void listUnrestrictedForAdmin() {
        when(collectionRejectionService.list(null, null)).thenReturn(List.of(
                CollectionRejectionRequest.builder().id(UUID.randomUUID()).agentId(agentId).collectionId(UUID.randomUUID())
                        .reason("Wrong amount").status(CollectionRejectionStatus.PENDING).requestedAt(Instant.now()).build()));

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .get()
                .uri("/api/v1/admin/collection-rejection-requests")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class).hasSize(1);
    }

    @Test
    void listUnauthenticatedRejected() {
        webTestClient.get()
                .uri("/api/v1/admin/collection-rejection-requests")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void approveSucceedsWithinOwnBranch() {
        UUID requestId = UUID.randomUUID();
        when(collectionRejectionService.findAgentIdForRequest(requestId)).thenReturn(agentId);
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(collectionRejectionProofStorageService.store(eq(requestId), any())).thenReturn(Mono.just("proofs/abc.jpg"));
        when(collectionRejectionService.approve(eq(requestId), eq("proofs/abc.jpg"), any())).thenReturn(
                CollectionRejectionRequest.builder().id(requestId).agentId(agentId).collectionId(UUID.randomUUID())
                        .reason("Wrong amount").status(CollectionRejectionStatus.APPROVED).requestedAt(Instant.now())
                        .proofPath("proofs/abc.jpg").build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/collection-rejection-requests/" + requestId + "/approve")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(proofOnlyBody())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.hasProof").isEqualTo(true);
    }

    @Test
    void approveOutsideBranchForbidden() {
        UUID requestId = UUID.randomUUID();
        when(collectionRejectionService.findAgentIdForRequest(requestId)).thenReturn(agentId);
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(UUID.randomUUID());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/collection-rejection-requests/" + requestId + "/approve")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(proofOnlyBody())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void denySucceedsWithinOwnBranch() {
        UUID requestId = UUID.randomUUID();
        when(collectionRejectionService.findAgentIdForRequest(requestId)).thenReturn(agentId);
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
        when(collectionRejectionService.deny(eq(requestId), eq("Not enough evidence"), any())).thenReturn(
                CollectionRejectionRequest.builder().id(requestId).agentId(agentId).collectionId(UUID.randomUUID())
                        .reason("Wrong amount").status(CollectionRejectionStatus.DENIED).requestedAt(Instant.now())
                        .decisionReason("Not enough evidence").build());

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.BRANCH_MANAGER, branchId)))
                .patch()
                .uri("/api/v1/admin/collection-rejection-requests/" + requestId + "/deny")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"Not enough evidence\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DENIED");
    }

    @Test
    void denyRejectsBlankReason() {
        UUID requestId = UUID.randomUUID();
        when(collectionRejectionService.findAgentIdForRequest(requestId)).thenReturn(agentId);
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);

        webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(adminAuthentication(AdminRole.ADMIN, null)))
                .patch()
                .uri("/api/v1/admin/collection-rejection-requests/" + requestId + "/deny")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"reason\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
