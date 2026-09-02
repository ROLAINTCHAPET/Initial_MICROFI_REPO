package com.microfi.savings.service;

import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.domain.AccessToken;
import com.microfi.savings.domain.AccessTokenStatus;
import com.microfi.savings.domain.ActivationPayment;
import com.microfi.savings.domain.ActivationRequest;
import com.microfi.savings.domain.ActivationRequestStatus;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.AccessTokenRepository;
import com.microfi.savings.repository.ActivationPaymentRepository;
import com.microfi.savings.repository.ActivationRequestRepository;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.shared.dto.CancelActivationRequestRequest;
import com.microfi.shared.dto.ClientActivateRequest;
import com.microfi.shared.dto.ClientActivationPendingResponse;
import com.microfi.shared.dto.ClientActivationResponse;
import com.microfi.shared.dto.ClientPaymentConfirmationRequest;
import com.microfi.shared.dto.MiddlewareFeeSplit;
import com.microfi.shared.dto.PendingActivationRequestResponse;
import com.microfi.shared.dto.PendingClientActivationResponse;
import com.microfi.transactions.service.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * UC-19 — Tokenized Client Activation / Annual Renewal. Three steps:
 * <ol>
 *   <li>{@link #selfActivate} — client enters their CBS Activation ID and sets login/PIN
 *       (verified via {@link CbsClientService}, never creates a CBS customer).</li>
 *   <li>{@link #sponsorActivation} — the agent, identifying the client by the login from step 1,
 *       registers that they've physically received the activation fee in cash — the client has no
 *       CBS balance to debit, so this works exactly like a {@link Collection}: real cash, counted
 *       against the agent's escrow ceiling (BR-03) via {@code CollectionService.enforceEscrowCeiling}.</li>
 *   <li>{@link #confirmPayment} — the client, in their own authenticated session, re-enters their
 *       PIN to confirm the agent's cash-receipt record is correct (BR-04).</li>
 * </ol>
 * Steps 2 and 3 form a two-party gate and may happen in either order — the 365-day booklet token
 * is only issued once <b>both</b> are done, so neither the agent nor the client can activate an
 * account unilaterally. {@link ActivationRequestRepository} tracks whichever side has confirmed
 * so far; {@link #finalizeIfReady} triggers the CBS fee split and token issuance the moment the
 * second confirmation lands. An agent may only have one pending gate open at a time (across all
 * clients) — {@code CollectionService.requireNoPendingActivation} blocks both a second
 * {@link #sponsorActivation} call and any regular {@code Collection} until the open one resolves,
 * since the cash for it is otherwise invisible to escrow-ceiling accounting in the meantime.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClientActivationService {

    private final ClientProfileRepository clientProfileRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final ActivationPaymentRepository activationPaymentRepository;
    private final ActivationRequestRepository activationRequestRepository;
    private final CbsClientService cbsClientService;
    private final PasswordEncoder passwordEncoder;
    private final CollectionService collectionService;
    private final MfiSettingsService mfiSettingsService;

    @Value("${client.activation.fee-xaf:1000}")
    private long activationFeeXaf;

    public ClientActivationPendingResponse selfActivate(ClientActivateRequest request) {
        // Proves membership against MICROFI's own client_profile mirror, seeded by the MFI's own
        // back office (POST /admin/clients) — not a separate CBS-issued code. This deployment only
        // ever serves the one MFI whose clients live in this database, so mfiMemberNo alone (the
        // account number the MFI already gave this person) is sufficient proof.
        ClientProfile client = clientProfileRepository.findByMfiMemberNo(request.getMfiIdentifier())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "'" + request.getMfiIdentifier() + "' isn't a recognised " + mfiSettingsService.getName()
                                + " account number. Ask your branch to register you first"));

        if (clientProfileRepository.findByLogin(request.getLogin()).filter(c -> !c.getId().equals(client.getId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login '" + request.getLogin() + "' is already taken");
        }

        client.setLogin(request.getLogin());
        client.setPinHash(passwordEncoder.encode(request.getPin()));
        clientProfileRepository.save(client);

        return ClientActivationPendingResponse.builder()
                .clientId(client.getId())
                .mfiMemberNo(client.getMfiMemberNo())
                .fullName(client.getFullName())
                .mfiName(mfiSettingsService.getName())
                .message("Credentials set. Ask your agent to sponsor activation, then confirm payment yourself to receive your digital booklet token.")
                .build();
    }

    public ClientActivationResponse sponsorActivation(String login, UUID agentId) {
        ClientProfile client = clientProfileRepository.findByLogin(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No client with login: " + login));
        return doSponsor(client, agentId);
    }

    /** Same gate as {@link #sponsorActivation}, identifying the client by id instead of login — backs the mobile app's pending-activation list (tap a client instead of typing their login). */
    public ClientActivationResponse sponsorActivationById(UUID clientId, UUID agentId) {
        ClientProfile client = clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
        return doSponsor(client, agentId);
    }

    private ClientActivationResponse doSponsor(ClientProfile client, UUID agentId) {
        requireNotAlreadyActive(client.getId());

        ActivationRequest activationRequest = openRequestFor(client.getId());
        if (activationRequest.getSponsoredAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already sponsored, awaiting client payment confirmation");
        }
        // Any other pending request already means this agent's cash-in-hand for it is invisible to
        // ceiling accounting until it resolves — see CollectionService.requireNoPendingActivation.
        collectionService.requireNoPendingActivation(agentId);
        collectionService.enforceEscrowCeiling(agentId, activationFeeXaf);
        activationRequest.setAgentId(agentId);
        activationRequest.setSponsoredAt(Instant.now());
        activationRequestRepository.save(activationRequest);

        return finalizeIfReady(client, activationRequest);
    }

    /** UC-19 step 2 candidate list for the mobile app's Sponsor Activation screen — same name/phone/member-number search as client lookup, restricted to clients who've self-activated but have no live booklet token yet. */
    public List<PendingClientActivationResponse> listPendingActivation(String query) {
        return clientProfileRepository.findPendingActivation(query == null ? "" : query.trim()).stream()
                .map(this::toPendingClientResponse)
                .toList();
    }

    private PendingClientActivationResponse toPendingClientResponse(ClientProfile client) {
        boolean sponsored = activationRequestRepository.findByClientIdAndStatus(client.getId(), ActivationRequestStatus.PENDING)
                .map(r -> r.getSponsoredAt() != null)
                .orElse(false);
        return PendingClientActivationResponse.builder()
                .id(client.getId())
                .mfiMemberNo(client.getMfiMemberNo())
                .fullName(client.getFullName())
                .phone(client.getPhone())
                .sponsored(sponsored)
                .build();
    }

    public ClientActivationResponse confirmPayment(UUID clientId, ClientPaymentConfirmationRequest request) {
        ClientProfile client = clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
        if (client.getPinHash() == null || !passwordEncoder.matches(request.getPin(), client.getPinHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid PIN");
        }
        requireNotAlreadyActive(clientId);

        ActivationRequest activationRequest = openRequestFor(clientId);
        if (activationRequest.getPaidAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment already confirmed, awaiting agent sponsorship");
        }
        activationRequest.setPaidAt(Instant.now());
        activationRequestRepository.save(activationRequest);

        return finalizeIfReady(client, activationRequest);
    }

    /**
     * Admin/branch-manager override for a stuck gate (client never confirmed, or agent
     * registered in error) — the only way to unblock an agent short of waiting for
     * {@code ActivationRequestExpiryJob}. Branch-scoping is the controller's job (needs the
     * client's branch, which this service has no reason to look up itself).
     */
    public PendingActivationRequestResponse cancelActivationRequest(UUID activationRequestId, UUID cancelledBy, CancelActivationRequestRequest request) {
        ActivationRequest activationRequest = activationRequestRepository.findById(activationRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activation request not found: " + activationRequestId));
        if (activationRequest.getStatus() != ActivationRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Activation request is not pending (already " + activationRequest.getStatus() + ")");
        }

        activationRequest.setStatus(ActivationRequestStatus.CANCELLED);
        activationRequest.setCancelledAt(Instant.now());
        activationRequest.setCancelledBy(cancelledBy);
        activationRequest.setCancelReason(request.getReason());
        activationRequestRepository.save(activationRequest);

        return toPendingResponse(activationRequest);
    }

    /** For a branch manager/admin investigating why an agent is blocked (should be 0 or 1 results — see the class javadoc). */
    public List<PendingActivationRequestResponse> listPendingForAgent(UUID agentId) {
        return activationRequestRepository.findByAgentIdAndStatus(agentId, ActivationRequestStatus.PENDING).stream()
                .map(this::toPendingResponse)
                .toList();
    }

    private PendingActivationRequestResponse toPendingResponse(ActivationRequest activationRequest) {
        return PendingActivationRequestResponse.builder()
                .id(activationRequest.getId())
                .clientId(activationRequest.getClientId())
                .agentId(activationRequest.getAgentId())
                .createdAt(activationRequest.getCreatedAt())
                .sponsoredAt(activationRequest.getSponsoredAt())
                .paidAt(activationRequest.getPaidAt())
                .build();
    }

    /** Finds the client's open gate, or starts a new one — not yet persisted until the caller sets its half and saves. */
    private ActivationRequest openRequestFor(UUID clientId) {
        return activationRequestRepository.findByClientIdAndStatus(clientId, ActivationRequestStatus.PENDING)
                .orElseGet(() -> ActivationRequest.builder().id(UUID.randomUUID()).clientId(clientId).build());
    }

    private void requireNotAlreadyActive(UUID clientId) {
        if (hasUnexpiredToken(clientId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client already has an active booklet token");
        }
    }

    private boolean hasUnexpiredToken(UUID clientId) {
        return accessTokenRepository.findByClientIdAndStatus(clientId, AccessTokenStatus.ACTIVE).stream()
                .anyMatch(token -> token.getExpiresAt().isAfter(Instant.now()));
    }

    private ClientActivationResponse finalizeIfReady(ClientProfile client, ActivationRequest activationRequest) {
        if (activationRequest.getSponsoredAt() == null) {
            return ClientActivationResponse.builder().clientId(client.getId()).status("AWAITING_SPONSORSHIP").build();
        }
        if (activationRequest.getPaidAt() == null) {
            return ClientActivationResponse.builder().clientId(client.getId()).status("AWAITING_PAYMENT").build();
        }

        // Renewal: supersede any previously-expired token so at most one ACTIVE-status row exists per client.
        List<AccessToken> existingActiveTokens = accessTokenRepository.findByClientIdAndStatus(client.getId(), AccessTokenStatus.ACTIVE);
        existingActiveTokens.forEach(token -> token.setStatus(AccessTokenStatus.REVOKED));
        accessTokenRepository.saveAll(existingActiveTokens);

        String idempotencyKey = "activation-" + activationRequest.getId();
        MiddlewareFeeSplit feeSplit = cbsClientService.splitFee(client.getCbsRef(), activationRequest.getAgentId().toString(), activationFeeXaf, idempotencyKey).block();
        if (feeSplit == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "CBS fee split did not respond");
        }

        activationPaymentRepository.save(ActivationPayment.builder()
                .id(UUID.randomUUID())
                .clientId(client.getId())
                .agentId(activationRequest.getAgentId())
                .amountXaf(activationFeeXaf)
                .agentCommissionXaf(feeSplit.getAgentCommissionXaf())
                .mfiShareXaf(feeSplit.getMfiShareXaf())
                .paidAt(Instant.now())
                .build());

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(365, ChronoUnit.DAYS);
        accessTokenRepository.save(AccessToken.builder()
                .id(UUID.randomUUID())
                .clientId(client.getId())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .status(AccessTokenStatus.ACTIVE)
                .build());

        activationRequest.setStatus(ActivationRequestStatus.COMPLETED);
        activationRequestRepository.save(activationRequest);

        return ClientActivationResponse.builder()
                .clientId(client.getId())
                .status("ACTIVE")
                .tokenExpiresAt(expiresAt)
                .agentCommissionXaf(feeSplit.getAgentCommissionXaf())
                .mfiShareXaf(feeSplit.getMfiShareXaf())
                .paymentReference(feeSplit.getReference())
                .build();
    }
}
