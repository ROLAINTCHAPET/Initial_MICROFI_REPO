package com.microfi.transactions.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.shared.dto.ExportBatchResponse;
import com.microfi.shared.dto.ExportRequest;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.ReconcileRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.VarianceRequest;
import com.microfi.transactions.service.OfjService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * UC-16/17/18 — End-of-Day. Mirrors architecture.txt section 11.1: {@code GET /ofj/{branchId}/summary},
 * {@code POST /ofj/{branchId}/reconcile}, {@code POST /ofj/{branchId}/variance},
 * {@code POST /ofj/{branchId}/export}. All branch-scoped Back-Office actions: ADMIN has global
 * scope, BRANCH_MANAGER/BRANCH_CASHIER only their own branch.
 */
@RestController
@RequestMapping("/api/v1/ofj/{branchId}")
@RequiredArgsConstructor
@Tag(name = "End-of-Day (OFJ)", description = "Digital cash desk reconciliation, variance regularization and daily CBS export")
public class OfjController {

    private final OfjService ofjService;

    @GetMapping("/summary")
    @Operation(summary = "OFJ Summary", description = "Current session state: digital totals per reconciled agent, physical totals and deltas so far. Any Back-Office role, own branch only.")
    public Mono<OfjSummaryResponse> summary(@PathVariable UUID branchId, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.getSummary(branchId))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    @PostMapping("/reconcile")
    @Operation(summary = "Reconcile Agent Cash", description = "Enters an agent's physical denomination count; computes delta = physical - digital (BR-01). Session auto-closes once every line is resolved. UC-16 actor: Branch Cashier (also ADMIN/BRANCH_MANAGER), own branch only.")
    public Mono<OfjAgentLineResponse> reconcile(@PathVariable UUID branchId, @Valid @RequestBody ReconcileRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER, AdminRole.BRANCH_CASHIER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.reconcile(branchId, request))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    @PostMapping("/variance")
    @Operation(summary = "Record Variance as Agent Debt", description = "UC-17: formalises a negative delta (shortage) as agent debt. Only shortages qualify (BR-Var-01). UC-17 actor: Branch Manager / Administrator, own branch only.")
    public Mono<VarianceDebtResponse> variance(@PathVariable UUID branchId, @Valid @RequestBody VarianceRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.recordVariance(request))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    @PostMapping("/export")
    @Operation(summary = "Daily CBS Export", description = "FR-18: submits the closed session to the CBS Middleware for posting. Requires the session to already be closed (BR-Export-01). UC-18 actor: Branch Cashier / Administrator, own branch only.")
    public Mono<ExportBatchResponse> export(@PathVariable UUID branchId, @Valid @RequestBody ExportRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_CASHIER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.exportDaily(branchId, request))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }
}
