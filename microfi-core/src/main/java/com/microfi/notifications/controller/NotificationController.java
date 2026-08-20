package com.microfi.notifications.controller;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.AgentDetails;
import com.microfi.notifications.service.NotificationService;
import com.microfi.shared.dto.NotificationLogResponse;
import com.microfi.shared.dto.NotifyCollectionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * UC-09 — Post-Validation Multi-Channel Notification. Mirrors architecture.txt section 11.1:
 * {@code POST /collections/{id}/notify}. A separate, explicit trigger from
 * {@code POST /collections} itself (rather than an automatic side effect of recording the
 * collection) so a failed/undelivered SMS can be retried by calling this again, and so the
 * "printed a receipt" flag can be reported after the fact once the agent's app actually attempts
 * the Bluetooth print.
 */
@RestController
@RequestMapping("/api/v1/collections/{id}")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Post-collection Flash SMS confirmation (FR-09)")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/notify")
    @Operation(summary = "Notify Client of Collection", description = "Sends the Flash SMS confirmation for a collection and records the receipt-printed flag (UC-09/FR-09). Only the agent who recorded the collection may trigger this.")
    public Mono<NotificationLogResponse> notify(@PathVariable("id") UUID collectionId,
                                                 @Valid @RequestBody NotifyCollectionRequest request,
                                                 Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(authentication -> ((AgentDetails) authentication.getPrincipal()).getAgent().getId())
                .flatMap(agentId -> notificationService.notifyCollection(collectionId, agentId, request));
    }

    @GetMapping("/notifications")
    @Operation(summary = "List Notification Attempts", description = "Audit trail of every SMS attempt for this collection (UC-09), most recent first. The recording agent or any Back-Office role may view it.")
    public Flux<NotificationLogResponse> listNotifications(@PathVariable("id") UUID collectionId, Mono<Authentication> authenticationMono) {
        return authenticationMono.flatMapMany(authentication -> {
            Object principal = authentication.getPrincipal();
            UUID callerAgentId;
            if (principal instanceof AdminUserDetails) {
                callerAgentId = null;
            } else if (principal instanceof AgentDetails agentDetails) {
                callerAgentId = agentDetails.getAgent().getId();
            } else {
                return Flux.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Back-office or the recording agent only"));
            }
            return Mono.fromCallable(() -> notificationService.listForCollection(collectionId, callerAgentId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMapMany(Flux::fromIterable);
        });
    }
}
