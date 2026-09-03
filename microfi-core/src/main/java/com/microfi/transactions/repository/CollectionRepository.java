package com.microfi.transactions.repository;

import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.CollectionConfirmedBy;
import com.microfi.transactions.domain.CollectionReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    Optional<Collection> findByAgentIdAndDeviceTxId(UUID agentId, String deviceTxId);

    /**
     * UC-16 / BR-03: every collection not yet swept into a reconciliation, regardless of which
     * calendar day it was collected on — both what OfjService#reconcile sums as an agent's
     * digital total, and (via CollectionService#enforceEscrowCeiling) their current cash-in-hand
     * against the escrow ceiling. A day-window sum alone would silently never count a collection
     * whose collectedAt has already rolled past "today" by the time a multi-day-offline agent's
     * backlog finally syncs, and would keep counting cash the agent no longer holds once it's
     * been reconciled.
     */
    @Query("SELECT COALESCE(SUM(c.amountXaf), 0) FROM Collection c "
            + "WHERE c.agentId = :agentId AND c.reconciledAt IS NULL AND c.voidedAt IS NULL AND c.collectedAt < :cutoff")
    long sumUnreconciledByAgent(@Param("agentId") UUID agentId, @Param("cutoff") Instant cutoff);

    /**
     * Distinct from {@link #sumUnreconciledByAgent} on purpose: this counts only collections a
     * cashier has genuinely never looked at yet ({@code UNRECONCILED}), not ones already swept
     * into a line and merely awaiting the agent's own confirmation ({@code
     * PENDING_AGENT_CONFIRMATION}, which still shows up in {@link #sumUnreconciledByAgent} because
     * it's still occupying the escrow ceiling). Using {@link #sumUnreconciledByAgent} here instead
     * would double-count an already-pending-but-unconfirmed collection into a repeat cashier sweep
     * run before the agent ever confirmed the first one — see OfjService#reconcile and
     * #listPendingAgents, the only two callers, both of which need "what's new since the cashier
     * last looked," not "what's still occupying the ceiling."
     */
    @Query("SELECT COALESCE(SUM(c.amountXaf), 0) FROM Collection c "
            + "WHERE c.agentId = :agentId AND c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.UNRECONCILED "
            + "AND c.voidedAt IS NULL AND c.collectedAt < :cutoff")
    long sumUncountedByAgent(@Param("agentId") UUID agentId, @Param("cutoff") Instant cutoff);

    /**
     * Marks exactly the rows {@link #sumUncountedByAgent} just summed as counted by the cashier's
     * physical count — same (agentId, cutoff) pair, so nothing summed is left unmarked and nothing
     * marked is left unsummed. Deliberately does NOT set {@code reconciledAt} — that's only set
     * once the agent themselves confirms (or the confirmation auto-expires), see
     * {@link #markAgentConfirmed} and {@link CollectionReconciliationStatus}'s doc. {@code
     * reconciledInLineId} IS stamped here regardless, so CBS export and branch-closing (both keyed
     * on it, never on {@code reconciledAt}) proceed on schedule independent of confirmation.
     */
    @Modifying
    @Query("UPDATE Collection c SET c.reconciledInLineId = :lineId, "
            + "c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION "
            + "WHERE c.agentId = :agentId AND c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.UNRECONCILED "
            + "AND c.voidedAt IS NULL AND c.collectedAt < :cutoff")
    int markPendingConfirmation(@Param("agentId") UUID agentId, @Param("cutoff") Instant cutoff, @Param("lineId") UUID lineId);

    /**
     * The agent's own confirmation (or the auto-expiry job past the configured timeout) — the
     * only thing that actually frees the escrow ceiling (see {@link #sumUnreconciledByAgent}).
     * Scoped to one reconciliation line, not one collection at a time: a cashier's count can
     * bundle dozens of collections, and per-collection confirmation taps would be unusable.
     */
    @Modifying
    @Query("UPDATE Collection c SET c.reconciledAt = :confirmedAt, "
            + "c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.CONFIRMED, c.confirmedBy = :confirmedBy "
            + "WHERE c.reconciledInLineId = :lineId "
            + "AND c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION")
    int markAgentConfirmed(@Param("lineId") UUID lineId, @Param("confirmedAt") Instant confirmedAt, @Param("confirmedBy") CollectionConfirmedBy confirmedBy);

    /** UC-16/18: exactly the collections a given set of OfjAgentLines reconciled, so CBS export posts what was actually reconciled rather than everything matching a calendar date. */
    List<Collection> findByReconciledInLineIdIn(List<UUID> lineIds);

    /** One reconciliation line's collections, for the agent's own drill-down (review before confirming, or picking one to request rejection on). */
    List<Collection> findByReconciledInLineId(UUID lineId);

    /**
     * Scoped to {@code PENDING_AGENT_CONFIRMATION} specifically, not every collection ever tied to
     * this {@code reconciledInLineId} — a repeat same-day cashier sweep reuses the same {@code
     * OfjAgentLine} row (see {@code OfjService#reconcile}'s find-or-create), so a line can end up
     * mixing an earlier, already-{@code CONFIRMED} batch with a newer {@code
     * PENDING_AGENT_CONFIRMATION} one under the same id. The pending-confirmations screen must show
     * only what's actually still awaiting the agent, not the line's whole history.
     */
    long countByReconciledInLineIdAndReconciliationStatus(UUID lineId, CollectionReconciliationStatus status);

    @Query("SELECT COALESCE(SUM(c.amountXaf), 0) FROM Collection c "
            + "WHERE c.reconciledInLineId = :lineId AND c.reconciliationStatus = :status")
    long sumByReconciledInLineIdAndReconciliationStatus(@Param("lineId") UUID lineId, @Param("status") CollectionReconciliationStatus status);

    /** Distinct lines still awaiting this agent's confirmation — AgentReconciliationController's pending-confirmations list. */
    @Query("SELECT DISTINCT c.reconciledInLineId FROM Collection c "
            + "WHERE c.agentId = :agentId AND c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION")
    List<UUID> findDistinctPendingConfirmationLineIdsByAgent(@Param("agentId") UUID agentId);

    /** Every line, across every agent/branch, still awaiting confirmation — CollectionConfirmationExpiryJob filters this down by the line's own age. */
    @Query("SELECT DISTINCT c.reconciledInLineId FROM Collection c "
            + "WHERE c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION")
    List<UUID> findDistinctPendingConfirmationLineIds();

    /** UC-11: an agent's collections for a specific calendar day, for the tracking map's route/transaction markers — deliberately date-scoped, unrelated to reconciliation status. */
    List<Collection> findByAgentIdInAndCollectedAtBetween(List<UUID> agentIds, Instant start, Instant end);

    /** UC-11/dashboard: an agent's own recent collections, newest first, for the mobile History/Recent Collections views. */
    List<Collection> findTop50ByAgentIdOrderByCollectedAtDesc(UUID agentId);

    /** UC-09-adjacent: a client's own recent collections, newest first — see CollectionDirectoryService#findRecentByClient. */
    List<Collection> findTop50ByClientIdOrderByCollectedAtDesc(UUID clientId);

    /** Back-Office client transactions export — every collection recorded against this client within an arbitrary [from, to) window. */
    List<Collection> findByClientIdAndCollectedAtBetween(UUID clientId, Instant start, Instant end);
}
