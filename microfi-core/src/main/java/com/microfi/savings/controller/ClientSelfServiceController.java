package com.microfi.savings.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.notifications.service.BroadcastMessageService;
import com.microfi.savings.ClientDetails;
import com.microfi.savings.service.ClientSelfService;
import com.microfi.shared.dto.AgentMisconductReportRequest;
import com.microfi.shared.dto.AgentMisconductReportResponse;
import com.microfi.shared.dto.BroadcastMessageResponse;
import com.microfi.shared.dto.ClientBalanceResponse;
import com.microfi.shared.dto.ClientHistoryEntryResponse;
import com.microfi.shared.dto.ClientProfileSelfResponse;
import com.microfi.shared.dto.ClientRecentCollectionResponse;
import com.microfi.transactions.service.AgentMisconductReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * UC-20/21/22 — My Profile / My Account / My Contribution History. Always read-only; per FR-20/21/22
 * none of these are blocked by an expired booklet token, so there is no token-status gate here —
 * only that the caller is an authenticated client.
 */
@RestController
@RequestMapping("/api/v1/clients/me")
@RequiredArgsConstructor
@Tag(name = "Client Self-Service", description = "Client profile, balance and contribution history (read-only)")
public class ClientSelfServiceController {

    private final ClientSelfService clientSelfService;
    private final CbsClientService cbsClientService;
    private final AgentMisconductReportService agentMisconductReportService;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;
    private final BroadcastMessageService broadcastMessageService;

    @GetMapping("/profile")
    @Operation(summary = "My Profile", description = "Identity and digital booklet token status (UC-20). Client cannot edit identity attributes.")
    public Mono<ClientProfileSelfResponse> profile(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> clientSelfService.getProfile(clientId))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/balance")
    @Operation(summary = "My Account Balance", description = "Live balance from the CBS (UC-21). Visible even if the booklet token has expired.")
    public Mono<ClientBalanceResponse> balance(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> clientSelfService.getCbsRef(clientId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(cbsClientService::getBalance)
                .map(balance -> ClientBalanceResponse.builder()
                        .balanceXaf(balance.getBalanceXaf())
                        .asOf(balance.getAsOf())
                        .build());
    }

    @GetMapping("/recent-collections")
    @Operation(summary = "My Recent Collections", description = "This client's own recent cash collections, newest first, straight from MICROFI — visible immediately after an agent validates one, unlike /history (CBS-backed), which only catches up at end-of-day export.")
    public Flux<ClientRecentCollectionResponse> recentCollections(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMapMany(clientId -> Mono.fromCallable(() -> clientSelfService.getRecentCollections(clientId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @GetMapping("/history")
    @Operation(summary = "My Contribution History", description = "Mini-statement replacing paper booklet pages (UC-22). Append-only from the client's perspective.")
    public Flux<ClientHistoryEntryResponse> history(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> clientSelfService.getCbsRef(clientId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMapMany(cbsClientService::getHistory)
                .map(entry -> ClientHistoryEntryResponse.builder()
                        .reference(entry.getReference())
                        .amountXaf(entry.getAmountXaf())
                        .date(entry.getDate())
                        .type(entry.getType())
                        .build());
    }

    @PostMapping("/misconduct-reports")
    @Operation(summary = "Report My Agent", description = "A client's report that their agent may have misbehaved (e.g. rude, demanded a bribe, wrong amount pocketed) — accepted best-effort, ADMIN/BRANCH_MANAGER see it immediately in the Back-Office for triage. Never blocked by an expired booklet token.")
    public Mono<AgentMisconductReportResponse> reportMisconduct(@Valid @RequestBody AgentMisconductReportRequest request, Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> {
                    UUID branchId = agentDirectoryService.requireBranchIdForAgent(request.getAgentId());
                    AgentMisconductReportResponse response = agentMisconductReportService.report(clientId, request);
                    auditService.record(AuditLogEntry.builder()
                            .category(AuditCategory.SECURITY)
                            .eventType("AGENT_MISCONDUCT_REPORTED")
                            .actorType(AuditActorType.CLIENT)
                            .actorId(clientId)
                            .branchId(branchId)
                            .agentId(request.getAgentId())
                            .detailsKey("AGENT_MISCONDUCT_REPORTED_DETAIL")
                            .build());
                    return response;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/broadcasts")
    @Operation(summary = "My Broadcast Messages", description = "Recent admin/branch-manager announcements addressed to clients — network-wide or this client's own branch. Polled by the mobile app, same pattern as an agent's branch notices.")
    public Flux<BroadcastMessageResponse> broadcasts(Mono<Authentication> authenticationMono) {
        return resolveClientId(authenticationMono)
                .flatMap(clientId -> Mono.fromCallable(() -> clientSelfService.getBranchId(clientId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMapMany(branchId -> Mono.fromCallable(() -> broadcastMessageService.listForClients(branchId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    private Mono<UUID> resolveClientId(Mono<Authentication> authenticationMono) {
        return authenticationMono.map(authentication -> ((ClientDetails) authentication.getPrincipal()).getClient().getId());
    }
}
