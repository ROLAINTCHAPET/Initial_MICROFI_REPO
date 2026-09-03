package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.CollectionRejectionRequestResponse;
import com.microfi.shared.dto.DenyCollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import com.microfi.transactions.service.CollectionRejectionProofStorageService;
import com.microfi.transactions.service.CollectionRejectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN/BRANCH_MANAGER review of an agent's request to void one of their own collections for
 * error — approval requires mandatory proof (multipart), denial only a reason, mirroring {@code
 * VarianceDebtController}'s write-off pattern exactly. Kept out of {@link OfjController} for the
 * same reason variance-debt governance is: these act on a request by its own id, with no natural
 * branchId in the URL the way every OFJ desk operation has.
 */
@RestController
@RequestMapping("/api/v1/admin/collection-rejection-requests")
@RequiredArgsConstructor
@Tag(name = "Collection Rejection Governance", description = "Admin/manager review of an agent's request to void a collection, with mandatory approval proof")
public class CollectionRejectionController {

    private final CollectionRejectionService collectionRejectionService;
    private final CollectionRejectionProofStorageService collectionRejectionProofStorageService;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "List Collection Rejection Requests", description = "Most recent first. ADMIN sees every branch; BRANCH_MANAGER sees only their own branch's agents.")
    public Flux<CollectionRejectionRequestResponse> list(@RequestParam(required = false) CollectionRejectionStatus status, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMapMany(caller -> Mono.fromCallable(() -> collectionRejectionService.list(scopedAgentIds(caller), status))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable))
                .map(this::toResponse);
    }

    @PatchMapping(value = "/{id}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Approve Collection Rejection", description = "Voids the collection; if it had already been posted to the CBS, reverses it there and notifies the client by SMS. Multipart: a mandatory 'proof' file part (PDF or JPEG). ADMIN or that agent's own branch's BRANCH_MANAGER.")
    public Mono<CollectionRejectionRequestResponse> approve(@PathVariable UUID id, @RequestPart("proof") FilePart proof, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> requireBranchScoped(caller, id))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(agentId -> collectionRejectionProofStorageService.store(id, proof)
                                .flatMap(proofPath -> Mono.fromCallable(() -> {
                                    CollectionRejectionRequest result = collectionRejectionService.approve(id, proofPath, caller.getAdminUser().getId());
                                    auditService.record(AuditLogEntry.builder()
                                            .category(AuditCategory.FINANCIAL)
                                            .eventType("COLLECTION_REJECTION_APPROVED")
                                            .actorType(AuditActorType.ADMIN)
                                            .actorId(caller.getAdminUser().getId())
                                            .actorLabel(caller.getAdminUser().getLogin())
                                            .actorRole(caller.getAdminUser().getRole())
                                            .branchId(agentDirectoryService.requireBranchIdForAgent(agentId))
                                            .agentId(agentId)
                                            .detailsKey("COLLECTION_REJECTION_APPROVED_DETAIL")
                                            .build());
                                    return toResponse(result);
                                }).subscribeOn(Schedulers.boundedElastic()))));
    }

    @PatchMapping("/{id}/deny")
    @Operation(summary = "Deny Collection Rejection", description = "The collection stays as-is. Mandatory reason, no proof needed. ADMIN or that agent's own branch's BRANCH_MANAGER.")
    public Mono<CollectionRejectionRequestResponse> deny(@PathVariable UUID id, @Valid @RequestBody DenyCollectionRejectionRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    UUID agentId = requireBranchScoped(caller, id);
                    CollectionRejectionRequest result = collectionRejectionService.deny(id, request.getReason(), caller.getAdminUser().getId());
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.FINANCIAL)
                            .eventType("COLLECTION_REJECTION_DENIED")
                            .actorType(AuditActorType.ADMIN)
                            .actorId(caller.getAdminUser().getId())
                            .actorLabel(caller.getAdminUser().getLogin())
                            .actorRole(caller.getAdminUser().getRole())
                            .branchId(agentDirectoryService.requireBranchIdForAgent(agentId))
                            .agentId(agentId)
                            .detailsKey("COLLECTION_REJECTION_DENIED_DETAIL")
                            .detailsParam1(request.getReason())
                            .build());
                    return toResponse(result);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/{id}/proof")
    @Operation(summary = "Download Approval Proof", description = "Streams the proof document attached to this request's approval. 404s if it was never approved. ADMIN or that agent's own branch's BRANCH_MANAGER.")
    public Mono<ResponseEntity<Resource>> proof(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    requireBranchScoped(caller, id);
                    return requireProofPath(id);
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(path -> collectionRejectionProofStorageService.load(path)
                        .map(resource -> ResponseEntity.ok().contentType(collectionRejectionProofStorageService.contentTypeFor(path)).body(resource)));
    }

    /** Resolves the request's agent and enforces branch scope on it — returns the agentId so callers don't need to re-resolve it. */
    private UUID requireBranchScoped(AdminUserDetails caller, UUID requestId) {
        UUID agentId = collectionRejectionService.findAgentIdForRequest(requestId);
        AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(agentId));
        return agentId;
    }

    private String requireProofPath(UUID requestId) {
        String path = collectionRejectionService.get(requestId).getProofPath();
        if (path == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No approval proof on file for this request");
        }
        return path;
    }

    /** null = unrestricted (ADMIN); otherwise the caller's own branch's agent ids. */
    private List<UUID> scopedAgentIds(AdminUserDetails caller) {
        if (caller.getAdminUser().getRole() == AdminRole.ADMIN) {
            return null;
        }
        return agentDirectoryService.findAgentIdsByBranch(caller.getAdminUser().getBranchId());
    }

    private CollectionRejectionRequestResponse toResponse(CollectionRejectionRequest r) {
        return CollectionRejectionRequestResponse.builder()
                .id(r.getId())
                .collectionId(r.getCollectionId())
                .agentId(r.getAgentId())
                .reason(r.getReason())
                .requestedAt(r.getRequestedAt())
                .status(r.getStatus().name())
                .reviewedBy(r.getReviewedBy())
                .reviewedAt(r.getReviewedAt())
                .decisionReason(r.getDecisionReason())
                .hasProof(r.getProofPath() != null)
                .build();
    }
}
