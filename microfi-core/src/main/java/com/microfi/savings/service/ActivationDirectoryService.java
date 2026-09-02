package com.microfi.savings.service;

import com.microfi.savings.domain.ActivationRequestStatus;
import com.microfi.savings.repository.ActivationPaymentRepository;
import com.microfi.savings.repository.ActivationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    /** UC-16 / BR-03: same reconciliation-sweep semantics as CollectionRepository#sumUnreconciledByAgent — see that Javadoc. */
    public long sumUnreconciled(UUID agentId, Instant cutoff) {
        return activationPaymentRepository.sumUnreconciledByAgent(agentId, cutoff);
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

    /** UC-16/18: line-level detail (not just a sum) for posting a branch's activation-fee cash to the CBS on export, same as Collection. */
    public List<ActivationCashLine> findByAgentIdsAndWindow(List<UUID> agentIds, Instant start, Instant end) {
        return activationPaymentRepository.findByAgentIdInAndPaidAtBetween(agentIds, start, end).stream()
                .map(payment -> new ActivationCashLine(payment.getId(), payment.getClientId(), payment.getAmountXaf(), payment.getPaidAt()))
                .toList();
    }

    /** UC-16/18: exactly the activation-fee cash a given set of OfjAgentLines reconciled, for CBS export — see CollectionRepository#findByReconciledInLineIdIn. */
    public List<ActivationCashLine> findByReconciledInLineIds(List<UUID> lineIds) {
        return activationPaymentRepository.findByReconciledInLineIdIn(lineIds).stream()
                .map(payment -> new ActivationCashLine(payment.getId(), payment.getClientId(), payment.getAmountXaf(), payment.getPaidAt()))
                .toList();
    }
}
