package com.microfi.savings.controller;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.AdminAccess;
import com.microfi.authentication.AgentDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.savings.ClientDetails;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.service.ClientActivationService;
import com.microfi.shared.dto.CancelActivationRequestRequest;
import com.microfi.shared.dto.ClientActivationResponse;
import com.microfi.shared.dto.ClientPaymentConfirmationRequest;
import com.microfi.shared.dto.PendingActivationRequestResponse;
import com.microfi.shared.dto.PendingClientActivationResponse;
import com.microfi.shared.dto.SponsorActivationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.UUID;

/**
 * UC-19's two-party activation gate. The client has no CBS balance to debit — they pay the
 * activation fee to the agent in cash, in person, the same way any {@code Collection} works.
 * Neither endpoint activates the booklet by itself — the 365-day token is only issued once both
 * have happened, in either order:
 * <ul>
 *   <li>{@code POST /clients/activation} — the agent identifies the client by their login and
 *       registers the cash payment they've received (counted against the agent's escrow ceiling,
 *       BR-03).</li>
 *   <li>{@code POST /clients/me/activation/pay} — the client, in their own authenticated session,
 *       re-enters their PIN to confirm the agent's cash-receipt record is correct (BR-04).</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Client Activation", description = "Two-party activation/renewal gate: agent cash-payment registration + client confirmation, fee split, 365-day booklet token issuance (FR-19)")
public class ClientActivationController {

    private final ClientActivationService clientActivationService;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;

    @PostMapping("/api/v1/clients/activation")
    @Operation(summary = "Register Client Activation Cash Payment", description = "The authenticated agent identifies the client by their login and registers the activation fee received in cash — checked against the agent's escrow ceiling (BR-03), same as a regular collection. Only finalizes (fee split + token issuance) once the client has also confirmed the payment.")
    public Mono<ClientActivationResponse> sponsor(@Valid @RequestBody SponsorActivationRequest request, Mono<Authentication> authenticationMono) {
        return resolveAgentId(authenticationMono)
                .flatMap(agentId -> Mono.fromCallable(() -> clientActivationService.sponsorActivation(request.getLogin(), agentId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(result -> auditActivationSponsored(agentId, result)));
    }

    @GetMapping("/api/v1/clients/pending-activation")
    @Operation(summary = "List Clients Awaiting Activation", description = "Clients who've already self-activated (set their own login) but have no live booklet token yet — the sponsor-activation candidate list for the mobile app. Same name/phone/member-number search as GET /clients/lookup. Not branch-scoped, same reasoning as client lookup. Agent principals only.")
    public Flux<PendingClientActivationResponse> pendingActivation(@RequestParam(defaultValue = "") String query, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(authentication -> {
                    if (!(authentication.getPrincipal() instanceof AgentDetails)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only agent accounts can list clients awaiting activation");
                    }
                    return query;
                })
                .flatMapMany(q -> Mono.fromCallable(() -> clientActivationService.listPendingActivation(q))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(Flux::fromIterable));
    }

    @PostMapping("/api/v1/clients/{clientId}/activation")
    @Operation(summary = "Register Client Activation Cash Payment (by client id)", description = "Same gate as POST /clients/activation, identifying the client by id instead of login — for the mobile app's pending-activation list (tap a client instead of typing their login).")
    public Mono<ClientActivationResponse> sponsorById(@PathVariable UUID clientId, Mono<Authentication> authenticationMono) {
        return resolveAgentId(authenticationMono)
                .flatMap(agentId -> Mono.fromCallable(() -> clientActivationService.sponsorActivationById(clientId, agentId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(result -> auditActivationSponsored(agentId, result)));
    }

    @PostMapping("/api/v1/clients/me/activation/pay")
    @Operation(summary = "Confirm Activation Payment", description = "The authenticated client re-enters their PIN to confirm the agent's cash-receipt record is correct (BR-04). Only finalizes (fee split + token issuance) once an agent has also registered the payment.")
    public Mono<ClientActivationResponse> confirmPayment(@Valid @RequestBody ClientPaymentConfirmationRequest request, Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(authentication -> ((ClientDetails) authentication.getPrincipal()).getClient())
                .flatMap(client -> Mono.fromCallable(() -> clientActivationService.confirmPayment(client.getId(), request))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(result -> auditActivationPaymentConfirmed(client, result)));
    }

    private void auditActivationSponsored(UUID agentId, ClientActivationResponse result) {
        var agentInfo = agentDirectoryService.findAuditInfo(agentId);
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.FINANCIAL)
                .eventType("CLIENT_ACTIVATION_SPONSORED")
                .actorType(AuditActorType.AGENT)
                .actorId(agentId)
                .actorLabel(agentInfo.username())
                .branchId(agentInfo.branchId())
                .agentId(agentId)
                .detailsKey("CLIENT_ACTIVATION_SPONSORED_DETAIL")
                .detailsParam1(result.getStatus())
                .detailsParam2(result.getPaymentReference())
                .build());
    }

    private void auditActivationPaymentConfirmed(ClientProfile client, ClientActivationResponse result) {
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.COMPLIANCE)
                .eventType("CLIENT_ACTIVATION_PAYMENT_CONFIRMED")
                .actorType(AuditActorType.CLIENT)
                .actorId(client.getId())
                .actorLabel(client.getLogin())
                .branchId(client.getBranchId())
                .detailsKey("CLIENT_ACTIVATION_PAYMENT_CONFIRMED_DETAIL")
                .detailsParam1(result.getStatus())
                .detailsParam2(result.getPaymentReference())
                .build());
    }

    @GetMapping("/api/v1/admin/agents/{id}/activation-requests/pending")
    @Operation(summary = "List Stuck Activation Gates", description = "Any still-open (agent registered, not yet client-confirmed) activation gate for this agent — normally 0 or 1, since an agent can only have one open at a time. Any Back-Office role, own branch only.")
    public Flux<PendingActivationRequestResponse> listPending(@PathVariable("id") UUID agentId, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono)
                .flatMapMany(caller -> Mono.fromCallable(() -> agentDirectoryService.requireBranchIdForAgent(agentId))
                        .doOnNext(branchId -> AdminAccess.requireBranchScope(caller, branchId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenMany(Mono.fromCallable(() -> clientActivationService.listPendingForAgent(agentId))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMapMany(Flux::fromIterable)));
    }

    @PatchMapping("/api/v1/admin/agents/{id}/activation-requests/{requestId}/cancel")
    @Operation(summary = "Cancel a Stuck Activation Gate", description = "Voids an activation gate that's stuck awaiting the other party's confirmation, unblocking the agent's ability to collect cash again. Mandatory justification. ADMIN or BRANCH_MANAGER, own branch only.")
    public Mono<PendingActivationRequestResponse> cancelPending(@PathVariable("id") UUID agentId, @PathVariable UUID requestId,
                                                                  @Valid @RequestBody CancelActivationRequestRequest request, Mono<Authentication> authenticationMono) {
        return AdminAccess.require(authenticationMono, AdminRole.ADMIN, AdminRole.BRANCH_MANAGER)
                .flatMap(caller -> Mono.fromCallable(() -> agentDirectoryService.requireBranchIdForAgent(agentId))
                        .doOnNext(branchId -> AdminAccess.requireBranchScope(caller, branchId))
                        .subscribeOn(Schedulers.boundedElastic())
                        .then(Mono.fromCallable(() -> clientActivationService.cancelActivationRequest(requestId, caller.getAdminUser().getId(), request))
                                .subscribeOn(Schedulers.boundedElastic())));
    }

    private Mono<UUID> resolveAgentId(Mono<Authentication> authenticationMono) {
        return authenticationMono.map(authentication -> ((AgentDetails) authentication.getPrincipal()).getAgent().getId());
    }

}
