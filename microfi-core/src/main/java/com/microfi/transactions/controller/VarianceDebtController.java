package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.WriteOffVarianceDebtRequest;
import com.microfi.transactions.service.OfjService;
import com.microfi.transactions.service.VarianceDebtProofStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * ADMIN-governance actions on an already-recorded {@code VarianceDebt} — writing off an agent's
 * shortage is a central-oversight decision, not something the branch that recorded the debt should
 * be able to reverse on its own (same reasoning as {@code AdminUserManagementController#updateRole}
 * being ADMIN-only). Kept out of {@link OfjController} since these act on a debt by its own id,
 * with no natural {@code branchId} in the URL the way every OFJ desk operation has.
 */
@RestController
@RequestMapping("/api/v1/admin/variance-debts/{id}")
@RequiredArgsConstructor
@Tag(name = "Variance Debt Governance", description = "Admin write-off of a recorded agent shortage, with mandatory reason and proof")
public class VarianceDebtController {

    private final OfjService ofjService;
    private final VarianceDebtProofStorageService varianceDebtProofStorageService;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;

    @PatchMapping(value = "/write-off", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Write Off Variance Debt", description = "Clears an OPEN shortage without altering the original record (BR-Var-02) — records who cleared it, why, and a supporting proof document instead. Multipart: a 'metadata' JSON part (reason) plus a 'proof' file part (PDF or JPEG), both mandatory. ADMIN only.")
    public Mono<VarianceDebtResponse> writeOff(@PathVariable UUID id,
                                                @Valid @RequestPart("metadata") WriteOffVarianceDebtRequest request,
                                                @RequestPart("proof") FilePart proof,
                                                Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> varianceDebtProofStorageService.store(id, proof)
                        .flatMap(proofPath -> Mono.fromCallable(() -> {
                                    VarianceDebtResponse result = ofjService.writeOffVarianceDebt(id, request.getReason(), proofPath, caller.getAdminUser().getId());
                                    auditService.record(AuditLogEntry.builder()
                                            .category(AuditCategory.SECURITY)
                                            .eventType("VARIANCE_DEBT_WRITTEN_OFF")
                                            .actorType(AuditActorType.ADMIN)
                                            .actorId(caller.getAdminUser().getId())
                                            .actorLabel(caller.getAdminUser().getLogin())
                                            .actorRole(caller.getAdminUser().getRole())
                                            .branchId(agentDirectoryService.requireBranchIdForAgent(result.getAgentId()))
                                            .agentId(result.getAgentId())
                                            .detailsKey("VARIANCE_DEBT_WRITTEN_OFF_DETAIL")
                                            .detailsParam1(String.valueOf(result.getAmountXaf()))
                                            .detailsParam2(request.getReason())
                                            .build());
                                    return result;
                                })
                                .subscribeOn(Schedulers.boundedElastic())));
    }

    @GetMapping("/write-off-proof")
    @Operation(summary = "Download Write-Off Proof", description = "Streams the proof document attached to this debt's write-off. 404s if the debt was never written off. ADMIN only.")
    public Mono<ResponseEntity<Resource>> writeOffProof(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN)
                .flatMap(caller -> Mono.fromCallable(() -> ofjService.requireWriteOffProofPath(id))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(path -> varianceDebtProofStorageService.load(path)
                        .map(resource -> ResponseEntity.ok().contentType(varianceDebtProofStorageService.contentTypeFor(path)).body(resource)));
    }
}
