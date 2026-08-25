package com.microfi.transactions.controller;

import com.microfi.authentication.AgentDetails;
import com.microfi.events.CollectionRecordDispatcher;
import com.microfi.transactions.service.CollectionService;
import com.microfi.shared.dto.CollectionRequest;
import com.microfi.shared.dto.CollectionResponse;
import com.microfi.shared.dto.CollectionSyncResult;
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
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * UC-06/07/08/12 — Digital Cash Desk. Mirrors architecture.txt section 11.1:
 * {@code POST /collections}, {@code POST /collections/sync}.
 * <p>
 * The acting agent is always resolved from the authenticated principal, never from a
 * client-supplied field — a collection must be attributable to whoever actually holds the
 * session, not whoever the caller claims to be.
 * <p>
 * Both writes go through {@link CollectionRecordDispatcher} rather than calling
 * {@link CollectionService#recordCollection} in-process — that's what gives a burst of agents
 * reconnecting at once real broker-buffered throttling instead of every request racing directly
 * for Core's thread pool. {@code myCollections} is a read with no burst-safety concern, so it
 * still calls {@code CollectionService} directly.
 */
@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
@Tag(name = "Collections", description = "Cash collection recording: GPS-gated, denomination-checked, escrow-checked")
public class CollectionController {

    private final CollectionService collectionService;
    private final CollectionRecordDispatcher collectionRecordDispatcher;

    @PostMapping
    @Operation(summary = "Record a Collection", description = "Deposit + mandatory denomination breakdown + mandatory geotag (BR-05/FR-12); rejected if it would exceed the agent's escrow ceiling (FR-04). Idempotent on (agent, deviceTxId).")
    public Mono<CollectionResponse> create(@Valid @RequestBody CollectionRequest request, Mono<Authentication> authenticationMono) {
        return resolveAgentId(authenticationMono)
                .flatMap(agentId -> collectionRecordDispatcher.dispatch(agentId, request));
    }

    @GetMapping
    @Operation(summary = "My Recent Collections", description = "The calling agent's own last 50 collections, newest first, with client names resolved — powers the mobile Home/History views.")
    public Flux<CollectionResponse> myCollections(Mono<Authentication> authenticationMono) {
        return resolveAgentId(authenticationMono)
                .flatMapMany(agentId -> Mono.fromCallable(() -> collectionService.findRecentByAgent(agentId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @PostMapping("/sync")
    @Operation(summary = "Batch Offline Sync", description = "Uploads a batch of collections recorded while offline (FR-07). Each item is processed independently so one rejected record doesn't block the rest of the day's sync.")
    public Flux<CollectionSyncResult> sync(@Valid @RequestBody List<CollectionRequest> requests, Mono<Authentication> authenticationMono) {
        return resolveAgentId(authenticationMono)
                .flatMapMany(agentId -> Flux.fromIterable(requests)
                        // concatMap, not flatMap: this agent's own items must still be recorded
                        // one at a time — the escrow-ceiling check reads today's cumulative total,
                        // so two of this same agent's items racing each other could both read the
                        // total before either commits and both pass individually even though their
                        // sum shouldn't. Cross-agent throttling now comes from the broker's bounded
                        // consumer pool instead, not from any ordering here.
                        .concatMap(request -> collectionRecordDispatcher.dispatch(agentId, request)
                                .map(response -> CollectionSyncResult.builder()
                                        .deviceTxId(request.getDeviceTxId())
                                        .success(true)
                                        .collection(response)
                                        .build())
                                .onErrorResume(ResponseStatusException.class, e -> Mono.just(CollectionSyncResult.builder()
                                        .deviceTxId(request.getDeviceTxId())
                                        .success(false)
                                        .error(e.getReason())
                                        .build()))));
    }

    private Mono<UUID> resolveAgentId(Mono<Authentication> authenticationMono) {
        return authenticationMono.map(authentication -> ((AgentDetails) authentication.getPrincipal()).getAgent().getId());
    }
}
