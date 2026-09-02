package com.microfi.transactions.controller;

import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.CollectionResponse;
import com.microfi.transactions.service.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Back-Office agent oversight — lets a branch manager, cashier, or admin review exactly which
 * collections an agent recorded on a given day and from which clients, independent of that
 * collection's reconciliation status. Same branch-scoping as every other admin-on-agent endpoint
 * (AdminTrackingController et al.): own branch only, unless ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/agents/{id}")
@RequiredArgsConstructor
@Tag(name = "Agent Collections Ledger", description = "Per-day itemized collections for a given agent, with client identity (Back-Office oversight)")
public class AdminAgentCollectionsController {

    private final CollectionService collectionService;
    private final AgentDirectoryService agentDirectoryService;

    @GetMapping("/collections")
    @Operation(summary = "Agent Collections by Day", description = "Every collection this agent recorded on the given calendar day, newest first, with the client's name resolved. Own branch only, unless ADMIN.")
    public Mono<List<CollectionResponse>> collections(@PathVariable UUID id,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                        Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(id));
                    return collectionService.findByAgentAndDay(id, date);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/collections/history")
    @Operation(summary = "Agent Collection History", description = "This agent's most recent collections (last 50) across any day, newest first, with the client's name resolved — the same query the mobile app's own History screen uses, reused here for Back-Office oversight. Own branch only, unless ADMIN.")
    public Mono<List<CollectionResponse>> history(@PathVariable UUID id, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(id));
                    return collectionService.findRecentByAgent(id);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    @GetMapping("/collections/range")
    @Operation(summary = "Agent Collections by Period", description = "Every collection this agent recorded within an arbitrary [from, to) window, newest first, with the client's name resolved — backs the Audit export's date-range picker. Own branch only, unless ADMIN.")
    public Mono<List<CollectionResponse>> range(@PathVariable UUID id,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                  Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMap(caller -> Mono.fromCallable(() -> {
                    AdminAccess.requireBranchScope(caller, agentDirectoryService.requireBranchIdForAgent(id));
                    return collectionService.findByAgentAndRange(id, from, to);
                }).subscribeOn(Schedulers.boundedElastic()));
    }
}
