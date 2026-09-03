package com.microfi.authentication.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.authentication.service.AgentSelfService;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.notifications.service.NotificationService;
import com.microfi.shared.dto.AgentResponse;
import com.microfi.shared.dto.BranchNoticeResponse;
import com.microfi.shared.dto.BranchResponse;
import com.microfi.shared.dto.CollectionRejectionRequestResponse;
import com.microfi.shared.dto.MfiNameResponse;
import com.microfi.shared.dto.ChangeAgentPinRequest;
import com.microfi.shared.dto.PendingReconciliationLineResponse;
import com.microfi.shared.dto.RequestCollectionRejectionRequest;
import com.microfi.shared.dto.RouteResponse;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.service.CollectionRejectionService;
import com.microfi.transactions.service.OfjService;
import com.microfi.transactions.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Gap found while building the mobile client: an agent's JWT carries only their employee code
 * (the {@code sub} claim), never their internal UUID — so there was no way for the app to resolve
 * "who am I" into the id that every other agent-facing endpoint (escrow, route, geofence) actually
 * needs. Mirrors the same principal resolution {@code CollectionController#resolveAgentId} already
 * uses, just exposed as a read-only self-lookup instead of an implicit per-request resolution.
 */
@RestController
@RequestMapping("/api/v1/agents/me")
@RequiredArgsConstructor
@Tag(name = "Agent Self-Service", description = "The authenticated agent's own profile")
public class AgentSelfController {

    private final TrackingService trackingService;
    private final BranchRepository branchRepository;
    private final AgentSelfService agentSelfService;
    private final AgentDirectoryService agentDirectoryService;
    private final NotificationService notificationService;
    private final MfiSettingsService mfiSettingsService;
    private final OfjService ofjService;
    private final CollectionRejectionService collectionRejectionService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "Get My Profile", description = "Resolves the caller's own agent record from their JWT — id, branch, phone, IMEI, status, whether the transaction PIN still needs to be set — everything the token itself doesn't carry. Agent principals only.")
    public Mono<AgentResponse> me(Mono<Authentication> authenticationMono) {
        return authenticationMono.map(authentication -> {
            Agent agent = requireAgent(authentication);
            return AgentResponse.builder()
                    .id(agent.getId())
                    .employeeCode(agent.getEmployeeCode())
                    .username(agent.getUsername())
                    .email(agent.getEmail())
                    .fullName(agent.getFullName())
                    .phone(agent.getPhone())
                    .imei(agent.getImei())
                    .branchId(agent.getBranchId())
                    .status(agent.getStatus())
                    .pinMustChange(Boolean.TRUE.equals(agent.getPinMustChange()))
                    .build();
        });
    }

    @GetMapping("/route")
    @Operation(summary = "My Route", description = "The caller's own ordered GPS trail plus that day's collection markers (UC-11, FR-11). Defaults to today (UTC) when no date is given. Agent principals only — the admin/back-office equivalent (any branch, any agent) lives at GET /admin/agents/{id}/route.")
    public Mono<RouteResponse> myRoute(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> trackingService.getRoute(agent.getId(), date != null ? date : LocalDate.now(ZoneOffset.UTC)))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/sos")
    @Operation(summary = "My SOS Alerts", description = "The caller's own raised SOS alerts, most recent first, including whether/when Back-Office acknowledged each one (UC-14) — the agent has no other way to know their distress signal was actually seen. Agent principals only.")
    public Flux<SosResponse> mySos(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMapMany(agent -> Mono.fromCallable(() -> trackingService.listSosEvents(List.of(agent.getId()), false))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @GetMapping("/branch")
    @Operation(summary = "My Branch", description = "The caller's own branch — name and contact number, for the mobile app's \"Contact Branch\" action. Agent principals only.")
    public Mono<BranchResponse> myBranch(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> {
                    Branch branch = branchRepository.findById(agent.getBranchId())
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + agent.getBranchId()));
                    return BranchResponse.builder()
                            .id(branch.getId())
                            .code(branch.getCode())
                            .name(branch.getName())
                            .phone(branch.getPhone())
                            .openTime(branch.getOpenTime())
                            .closeTime(branch.getCloseTime())
                            .openTimeLocked(agentDirectoryService.isBranchPastOpenTime(branch))
                            .timezone(branch.getTimezone())
                            .maxCashiers(branch.effectiveMaxCashiers())
                            .requireImei(branch.effectiveRequireImei())
                            .build();
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/mfi-name")
    @Operation(summary = "MFI Institutional Name", description = "The organization name to print on a receipt (BR-Notif-01's mandatory legal mentions) — cached client-side so an offline collection can still compose a compliant receipt without reaching the server. Agent principals only.")
    public Mono<MfiNameResponse> mfiName(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> MfiNameResponse.builder().name(mfiSettingsService.getName()).build())
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/branch-notices")
    @Operation(summary = "My Branch's Recent Notices", description = "The caller's own branch's most recent operational notices (e.g. a same-day closing-time change), newest first (UC-15). Polled by the mobile app — there's no push infrastructure in this app, same reasoning as SOS acknowledgement. Agent principals only.")
    public Flux<BranchNoticeResponse> myBranchNotices(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMapMany(agent -> Mono.fromCallable(() -> notificationService.listRecentNoticesForBranch(agent.getBranchId()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @GetMapping("/pending-confirmations")
    @Operation(summary = "My Pending Reconciliation Confirmations", description = "Reconciliation lines a cashier has physically counted but that still need this agent's own sign-off before the cash stops counting against their escrow ceiling — polled by the mobile app the same way branch notices/SOS acknowledgement are (no push infrastructure in this app). Agent principals only.")
    public Flux<PendingReconciliationLineResponse> myPendingConfirmations(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMapMany(agent -> Mono.fromCallable(() -> ofjService.listPendingConfirmationLines(agent.getId()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @GetMapping("/reconciliations/{lineId}/collections")
    @Operation(summary = "My Reconciliation Line's Collections", description = "The individual collections behind one pending-confirmation line — lets the agent review exactly what's in it before confirming, or pick one to request rejection on. Agent principals only, and only for their own line.")
    public Flux<com.microfi.shared.dto.CollectionResponse> myReconciliationLineCollections(@PathVariable UUID lineId, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMapMany(agent -> Mono.fromCallable(() -> ofjService.listCollectionsForLine(agent.getId(), lineId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @PostMapping("/reconciliations/{lineId}/confirm")
    @Operation(summary = "Confirm A Reconciliation", description = "Attests the cashier's physical count for this line was correct — the only thing that actually frees the cash counted from this agent's escrow ceiling. Agent principals only, and only for their own line.")
    public Mono<Void> confirmReconciliation(@PathVariable UUID lineId, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromRunnable(() -> {
                    ofjService.confirmReconciliation(agent.getId(), lineId);
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.FINANCIAL)
                            .eventType("COLLECTION_RECONCILIATION_CONFIRMED")
                            .actorType(AuditActorType.AGENT)
                            .actorId(agent.getId())
                            .actorLabel(agent.getUsername())
                            .branchId(agent.getBranchId())
                            .agentId(agent.getId())
                            .detailsKey("COLLECTION_RECONCILIATION_CONFIRMED_DETAIL")
                            .detailsParam1("AGENT")
                            .build());
                }).subscribeOn(Schedulers.boundedElastic())).then();
    }

    @PostMapping("/collections/{id}/reject-request")
    @Operation(summary = "Request Collection Rejection", description = "Asks a branch manager/admin to void one of this agent's own collections for error (wrong amount, wrong client, duplicate entry). Requires mandatory approval proof on the reviewer's side before anything actually changes (UC pending: two-party gate, see CollectionRejectionRequest). Agent principals only, and only for their own collection.")
    public Mono<CollectionRejectionRequestResponse> requestCollectionRejection(@PathVariable UUID id, @Valid @RequestBody RequestCollectionRejectionRequest request, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> {
                    CollectionRejectionRequest result = collectionRejectionService.requestRejection(agent.getId(), id, request.getReason());
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.FINANCIAL)
                            .eventType("COLLECTION_REJECTION_REQUESTED")
                            .actorType(AuditActorType.AGENT)
                            .actorId(agent.getId())
                            .actorLabel(agent.getUsername())
                            .branchId(agent.getBranchId())
                            .agentId(agent.getId())
                            .detailsKey("COLLECTION_REJECTION_REQUESTED_DETAIL")
                            .detailsParam1(request.getReason())
                            .build());
                    return CollectionRejectionRequestResponse.builder()
                            .id(result.getId())
                            .collectionId(result.getCollectionId())
                            .agentId(result.getAgentId())
                            .reason(result.getReason())
                            .requestedAt(result.getRequestedAt())
                            .status(result.getStatus().name())
                            .hasProof(false)
                            .build();
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @PatchMapping("/pin")
    @Operation(summary = "Change My Transaction PIN", description = "Replaces the transaction PIN checked on every collection (never the login password). Used both for the mandatory first-time replacement of the admin-assigned starting PIN and any later voluntary change. Agent principals only.")
    public Mono<AgentResponse> changePin(@Valid @RequestBody ChangeAgentPinRequest request, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(this::requireAgent)
                .flatMap(agent -> Mono.fromCallable(() -> agentSelfService.changePin(agent, request))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(saved -> AgentResponse.builder()
                        .id(saved.getId())
                        .employeeCode(saved.getEmployeeCode())
                        .username(saved.getUsername())
                        .email(saved.getEmail())
                        .fullName(saved.getFullName())
                        .phone(saved.getPhone())
                        .imei(saved.getImei())
                        .branchId(saved.getBranchId())
                        .status(saved.getStatus())
                        .pinMustChange(Boolean.TRUE.equals(saved.getPinMustChange()))
                        .build());
    }

    private Agent requireAgent(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof AgentDetails agentDetails)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only agent accounts have a self-profile");
        }
        return agentDetails.getAgent();
    }
}
