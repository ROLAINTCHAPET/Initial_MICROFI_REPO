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
     * Scoped to {@code locationName} alone — {@link com.microfi.transactions.service.CollectionGeocodeListener}
     * used to load+mutate+save the whole entity, which meant Hibernate's default (non-{@code
     * @DynamicUpdate}) UPDATE wrote back every column from whatever snapshot the listener's
     * findById captured, including {@code reconciliationStatus}/{@code reconciledInLineId}.
     * Reverse-geocoding a collection can take several seconds (retries against a slow/unreachable
     * provider) or simply lands slightly after a cashier reconciles the very collection it's
     * resolving; either way a load-then-save race that wide silently reverted a collection that
     * had just been swept into a reconciliation line back to {@code UNRECONCILED}, undoing the
     * cashier's count with no error anywhere. A single-column update can't clobber a concurrent
     * change to a different column no matter how it interleaves.
     */
    @Modifying
    @Query("UPDATE Collection c SET c.locationName = :locationName WHERE c.id = :id")
    void updateLocationName(@Param("id") UUID id, @Param("locationName") String locationName);

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
     * The literal "collected today" figure — every calendar-day collection regardless of
     * reconciliation status, deliberately distinct from {@link #sumUnreconciledByAgent} (which is
     * day-agnostic cash-in-hand for the BR-03 ceiling gate, not a display-friendly "today" total).
     * Using that ceiling figure under a "Collected Today" label was the actual bug: a multi-day
     * backlog agent showed inflated "today" totals, and an agent who'd already reconciled
     * everything they collected today showed zero. For display only — never feed this into
     * ceiling/reconciliation logic, which must stay day-agnostic.
     */
    @Query("SELECT COALESCE(SUM(c.amountXaf), 0) FROM Collection c "
            + "WHERE c.agentId = :agentId AND c.voidedAt IS NULL AND c.collectedAt >= :startOfDay AND c.collectedAt < :endOfDay")
    long sumCollectedTodayByAgent(@Param("agentId") UUID agentId, @Param("startOfDay") Instant startOfDay, @Param("endOfDay") Instant endOfDay);

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

    /**
     * The real export-eligibility gate: a collection must have cleared BOTH the cashier's physical
     * count (reconciledInLineId set) AND the agent's own sign-off (reconciliationStatus ==
     * CONFIRMED, via #markAgentConfirmed or its 48h auto-expiry) before it may reach the CBS — a
     * merely-{@code PENDING_AGENT_CONFIRMATION} collection is NOT export-eligible, closing the gap
     * where the old {@link #findByReconciledInLineIdIn} let the cashier's count alone trigger a
     * real posting the agent never actually confirmed. {@code exportedAt IS NULL} is what makes
     * repeated export runs over the same lineIds idempotent instead of double-posting on a re-run
     * (see OfjService's "End My Day"/scheduled-closing-time triggers, which can both fire after a
     * branch's own export already ran).
     */
    List<Collection> findByReconciledInLineIdInAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
            List<UUID> lineIds, CollectionReconciliationStatus status);

    /**
     * Area C ("End My Day"): every not-yet-exported, agent-confirmed collection belonging to one
     * agent, regardless of which reconciliation line/session it landed in. Deliberately
     * agent-scoped rather than session-scoped — the whole point is letting one agent push their
     * own confirmed cash immediately without waiting on the rest of the branch to balance or on
     * the session to close.
     */
    List<Collection> findByAgentIdAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
            UUID agentId, CollectionReconciliationStatus status);

    @Query("SELECT COALESCE(SUM(c.amountXaf), 0) FROM Collection c "
            + "WHERE c.agentId = :agentId AND c.reconciliationStatus = :status AND c.voidedAt IS NULL AND c.exportedAt IS NULL")
    long sumByAgentIdAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
            @Param("agentId") UUID agentId, @Param("status") CollectionReconciliationStatus status);

    long countByAgentIdAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
            UUID agentId, CollectionReconciliationStatus status);

    /** One reconciliation line's collections, for the agent's own drill-down (review before confirming, or picking one to request rejection on). */
    List<Collection> findByReconciledInLineId(UUID lineId);

    /**
     * Scoped to {@code PENDING_AGENT_CONFIRMATION} specifically, not every collection ever tied to
     * this {@code reconciledInLineId} — a repeat same-day cashier sweep reuses the same {@code
     * OfjAgentLine} row (see {@code OfjService#reconcile}'s find-or-create), so a line can end up
     * mixing an earlier, already-{@code CONFIRMED} batch with a newer {@code
     * PENDING_AGENT_CONFIRMATION} one under the same id. The pending-confirmations screen must show
     * only what's actually still awaiting the agent, not the line's whole history. Excludes voided
     * rows: approving a rejection request never changes {@code reconciliationStatus} (see
     * CollectionRejectionService#approve), only stamps {@code voidedAt} — without this filter a
     * collection whose rejection was just approved would keep counting as "awaiting confirmation"
     * forever instead of dropping off the line's pending count.
     */
    long countByReconciledInLineIdAndReconciliationStatusAndVoidedAtIsNull(UUID lineId, CollectionReconciliationStatus status);

    /** Collections under this line whose rejection request was approved — drives the /ofj "Rejected" badge, taking priority over the plain pending-confirmation count. */
    long countByReconciledInLineIdAndVoidedAtIsNotNull(UUID lineId);

    /** Same set {@link #countByReconciledInLineIdAndVoidedAtIsNotNull} counts, fetched in full so the /ofj "Rejected" badge can show what was actually rejected (see OfjService#toLineResponse). */
    List<Collection> findByReconciledInLineIdAndVoidedAtIsNotNull(UUID lineId);

    @Query("SELECT COALESCE(SUM(c.amountXaf), 0) FROM Collection c "
            + "WHERE c.reconciledInLineId = :lineId AND c.reconciliationStatus = :status AND c.voidedAt IS NULL")
    long sumByReconciledInLineIdAndReconciliationStatus(@Param("lineId") UUID lineId, @Param("status") CollectionReconciliationStatus status);

    /**
     * Distinct lines still awaiting this agent's confirmation — AgentReconciliationController's
     * pending-confirmations list. {@code voidedAt IS NULL} matters here specifically: approving a
     * rejection request never changes the rejected collection's own {@code reconciliationStatus}
     * (see CollectionRejectionService#approve, which only stamps {@code voidedAt}) — without this
     * filter, a collection that was {@code PENDING_AGENT_CONFIRMATION} at the moment its rejection
     * was approved would leave a permanent "ghost" line here forever: the count/sum queries
     * already exclude it correctly, but the line itself would still surface with nothing real left
     * in it, showing the agent a phantom "awaiting confirmation" they have nothing to act on.
     */
    @Query("SELECT DISTINCT c.reconciledInLineId FROM Collection c "
            + "WHERE c.agentId = :agentId AND c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION "
            + "AND c.voidedAt IS NULL")
    List<UUID> findDistinctPendingConfirmationLineIdsByAgent(@Param("agentId") UUID agentId);

    /** Every line, across every agent/branch, still awaiting confirmation — CollectionConfirmationExpiryJob filters this down by the line's own age. See {@link #findDistinctPendingConfirmationLineIdsByAgent}'s doc for why voidedAt IS NULL matters here too. */
    @Query("SELECT DISTINCT c.reconciledInLineId FROM Collection c "
            + "WHERE c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION "
            + "AND c.voidedAt IS NULL")
    List<UUID> findDistinctPendingConfirmationLineIds();

    /** Branch-wide equivalent of {@link #findDistinctPendingConfirmationLineIdsByAgent} — backs the Back-Office "En attente" view (OfjService#listPendingConfirmationsForBranch). Same voidedAt IS NULL reasoning. */
    @Query("SELECT DISTINCT c.reconciledInLineId FROM Collection c "
            + "WHERE c.agentId IN :agentIds AND c.reconciliationStatus = com.microfi.transactions.domain.CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION "
            + "AND c.voidedAt IS NULL")
    List<UUID> findDistinctPendingConfirmationLineIdsByAgentIn(@Param("agentIds") List<UUID> agentIds);

    /** UC-11: an agent's collections for a specific calendar day, for the tracking map's route/transaction markers — deliberately date-scoped, unrelated to reconciliation status. */
    List<Collection> findByAgentIdInAndCollectedAtBetween(List<UUID> agentIds, Instant start, Instant end);

    /** UC-11/dashboard: an agent's own recent collections, newest first, for the mobile History/Recent Collections views. */
    List<Collection> findTop50ByAgentIdOrderByCollectedAtDesc(UUID agentId);

    /** UC-09-adjacent: a client's own recent collections, newest first — see CollectionDirectoryService#findRecentByClient. */
    List<Collection> findTop50ByClientIdOrderByCollectedAtDesc(UUID clientId);

    /** Back-Office client transactions export — every collection recorded against this client within an arbitrary [from, to) window. */
    List<Collection> findByClientIdAndCollectedAtBetween(UUID clientId, Instant start, Instant end);

    /** Bulk counterpart to {@link #findByClientIdAndCollectedAtBetween} — every collection for every client in a branch (or any other client-id set) within an arbitrary [from, to) window, for a CBS-import/audit CSV export spanning the whole branch rather than one client at a time. */
    List<Collection> findByClientIdInAndCollectedAtBetween(List<UUID> clientIds, Instant start, Instant end);
}
