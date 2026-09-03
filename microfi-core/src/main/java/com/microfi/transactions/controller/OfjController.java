package com.microfi.transactions.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.shared.dto.ExportBatchResponse;
import com.microfi.shared.dto.ExportRequest;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjPendingLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.ReconcileRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.VarianceRequest;
import com.microfi.transactions.service.OfjService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
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
    private final AuditService auditService;

    @GetMapping("/summary")
    @Operation(summary = "OFJ Summary", description = "Current session state: digital totals per reconciled agent, physical totals and deltas so far. Omit `date` for today (auto-creates the session if needed); any other date is read-only history and 404s if that day never had a session. Any Back-Office role, own branch only.")
    public Mono<OfjSummaryResponse> summary(@PathVariable UUID branchId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.getSummary(branchId, date))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    @GetMapping("/pending")
    @Operation(summary = "Pending Reconciliation Queue", description = "Active agents in the branch who've collected cash today but haven't been reconciled yet — the cashier's real \"who's next\" queue (not individually tabled in architecture.txt's endpoint list, but required to back UC-16's nominal flow: a cashier needs to see who's waiting before reconciling them, not just the outcome afterward). Any Back-Office role, own branch only.")
    public Flux<OfjPendingLineResponse> pending(@PathVariable UUID branchId, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.listPendingAgents(branchId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                });
    }

    @GetMapping("/history")
    @Operation(summary = "OFJ History", description = "Every past session for the branch, most recent business date first — for a reports/history screen. Omit from/to for the full unbounded history; pass both to restrict to a chosen period (used by the Audit export's date-range picker). Any Back-Office role, own branch only.")
    public Flux<OfjSummaryResponse> history(@PathVariable UUID branchId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> (from != null && to != null)
                                    ? ofjService.listHistory(branchId, from, to)
                                    : ofjService.listHistory(branchId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                });
    }

    @GetMapping("/variance-debts")
    @Operation(summary = "Branch Variance Debts", description = "Every agent debt recorded in the branch, most recent first — for a \"who owes what\" dashboard. Any Back-Office role, own branch only.")
    public Flux<VarianceDebtResponse> varianceDebts(@PathVariable UUID branchId,
                                                      @RequestParam(defaultValue = "false") boolean openOnly,
                                                      Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.listVarianceDebtsForBranch(branchId, openOnly))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                });
    }

    @PostMapping("/reconcile")
    @Operation(summary = "Reconcile Agent Cash", description = "Enters an agent's physical denomination count; computes delta = physical - digital (BR-01). Session auto-closes once every line is resolved. UC-16 actor: Branch Cashier (also ADMIN/BRANCH_MANAGER), own branch only.")
    public Mono<OfjAgentLineResponse> reconcile(@PathVariable UUID branchId, @Valid @RequestBody ReconcileRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER, AdminRole.BRANCH_CASHIER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> {
                        OfjAgentLineResponse result = ofjService.reconcile(branchId, request);
                        auditService.record(AuditLogEntry.builder()
                                .category(AuditCategory.FINANCIAL)
                                .eventType("COLLECTION_RECONCILIATION_SUBMITTED")
                                .actorType(AuditActorType.ADMIN)
                                .actorId(caller.getAdminUser().getId())
                                .actorLabel(caller.getAdminUser().getLogin())
                                .actorRole(caller.getAdminUser().getRole())
                                .branchId(branchId)
                                .agentId(request.getAgentId())
                                .detailsKey("COLLECTION_RECONCILIATION_SUBMITTED_DETAIL")
                                .detailsParam1(String.valueOf(result.getPhysicalTotalXaf()))
                                .detailsParam2(String.valueOf(result.getDeltaXaf()))
                                .build());
                        return result;
                    }).subscribeOn(Schedulers.boundedElastic());
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
    @Operation(summary = "Daily CBS Export", description = "FR-18: submits the closed session to the CBS Middleware for posting. Requires the session to already be closed (BR-Export-01). UC-18 actor: Branch Cashier / Administrator, own branch only — a Branch Manager oversees everything a cashier at their branch can do, so they're included too.")
    public Mono<ExportBatchResponse> export(@PathVariable UUID branchId, @Valid @RequestBody ExportRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER, AdminRole.BRANCH_CASHIER)
                .flatMap(caller -> {
                    AdminAccess.requireBranchScope(caller, branchId);
                    return Mono.fromCallable(() -> ofjService.exportDaily(branchId, request))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }
}
