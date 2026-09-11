package com.microfi.savings.service;

import com.microfi.savings.domain.AccessTokenStatus;
import com.microfi.savings.domain.ActivationRequestStatus;
import com.microfi.savings.repository.AccessTokenRepository;
import com.microfi.savings.repository.ActivationPaymentRepository;
import com.microfi.savings.repository.ActivationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code savings}'s public contract for other modules that need to read agent-side activation
 * state without reaching into {@link ActivationPaymentRepository}/{@link ActivationRequestRepository}
 * directly — e.g. {@code transactions.CollectionService} folding activation-fee cash into the same
 * escrow-ceiling check (BR-03) regular collections use, and blocking new cash intake while an agent
 * has an unresolved activation gate open. Mirrors {@link ClientDirectoryService}'s pattern for
 * cross-module reads.
 */
@Service
@RequiredArgsConstructor
public class ActivationDirectoryService {

    private final ActivationPaymentRepository activationPaymentRepository;
    private final ActivationRequestRepository activationRequestRepository;
    private final AccessTokenRepository accessTokenRepository;

    /** UC-16 / BR-03: same reconciliation-sweep semantics as CollectionRepository#sumUnreconciledByAgent — see that Javadoc. */
    public long sumUnreconciled(UUID agentId, Instant cutoff) {
        return activationPaymentRepository.sumUnreconciledByAgent(agentId, cutoff);
    }

    /** Display-only calendar-day total — see CollectionRepository#sumCollectedTodayByAgent's Javadoc. */
    public long sumCollectedToday(UUID agentId, Instant startOfDay, Instant endOfDay) {
        return activationPaymentRepository.sumCollectedTodayByAgent(agentId, startOfDay, endOfDay);
    }

    /** Marks exactly the payments {@link #sumUnreconciled} just summed as reconciled. */
    public void markReconciled(UUID agentId, Instant cutoff, UUID lineId) {
        activationPaymentRepository.markReconciled(agentId, cutoff, lineId);
    }

    /**
     * True if the agent has registered cash for an activation the client hasn't confirmed yet (or
     * vice versa) — until that gate closes, the agent's cash-in-hand for that client is invisible
     * to escrow-ceiling accounting (it isn't a finalized {@code ActivationPayment} yet), so no
     * further cash of any kind should be accepted from them in the meantime.
     */
    public boolean hasPendingActivation(UUID agentId) {
        return activationRequestRepository.existsByAgentIdAndStatus(agentId, ActivationRequestStatus.PENDING);
    }

    /**
     * UC-19 gate (opt-in per branch, see {@code Branch#requireClientActivation}): a client counts
     * as activated only while their most recent non-revoked token is both {@code ACTIVE} and not
     * past {@code expiresAt} — a token that lapsed needs renewal (a fresh activation payment)
     * before this branch will accept more cash for that client, same as a first-time activation.
     */
    public boolean isClientActivated(UUID clientId) {
        return accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE)
                .filter(token -> token.getExpiresAt() == null || token.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    /** {@code CollectionService.recordCollection}'s gate, only invoked when the agent's branch opts into {@link #isClientActivated}. */
    public void requireActivatedClient(UUID clientId) {
        if (!isClientActivated(clientId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This client hasn't completed activation yet — sponsor and complete their activation before collecting cash");
        }
    }

    /** UC-16/18: line-level detail (not just a sum) for posting a branch's activation-fee cash to the CBS on export, same as Collection. */
    public List<ActivationCashLine> findByAgentIdsAndWindow(List<UUID> agentIds, Instant start, Instant end) {
        return activationPaymentRepository.findByAgentIdInAndPaidAtBetween(agentIds, start, end).stream()
                .map(payment -> new ActivationCashLine(payment.getId(), payment.getClientId(), payment.getAmountXaf(), payment.getPaidAt()))
                .toList();
    }

    /** UC-16/18: exactly the not-yet-exported activation-fee cash a given set of OfjAgentLines reconciled, for CBS export — see CollectionRepository#findByReconciledInLineIdInAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull. */
    public List<ActivationCashLine> findByReconciledInLineIds(List<UUID> lineIds) {
        return activationPaymentRepository.findByReconciledInLineIdInAndExportedAtIsNull(lineIds).stream()
                .map(payment -> new ActivationCashLine(payment.getId(), payment.getClientId(), payment.getAmountXaf(), payment.getPaidAt()))
                .toList();
    }

    /** Stamps exportedAt/cbsTransactionRef after a successful CBS post — same tracking pair as Collection#exportedAt, required now that export can run more than once per session. */
    public void markExported(UUID paymentId, Instant exportedAt, String cbsTransactionRef) {
        activationPaymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.setExportedAt(exportedAt);
            payment.setCbsTransactionRef(cbsTransactionRef);
            activationPaymentRepository.save(payment);
        });
    }
}
