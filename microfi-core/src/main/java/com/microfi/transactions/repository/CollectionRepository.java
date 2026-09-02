package com.microfi.transactions.repository;

import com.microfi.transactions.domain.Collection;
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
            + "WHERE c.agentId = :agentId AND c.reconciledAt IS NULL AND c.collectedAt < :cutoff")
    long sumUnreconciledByAgent(@Param("agentId") UUID agentId, @Param("cutoff") Instant cutoff);

    /** Marks exactly the rows {@link #sumUnreconciledByAgent} just summed as reconciled — same (agentId, cutoff) pair, so nothing summed is left unmarked and nothing marked is left unsummed. */
    @Modifying
    @Query("UPDATE Collection c SET c.reconciledAt = :cutoff, c.reconciledInLineId = :lineId "
            + "WHERE c.agentId = :agentId AND c.reconciledAt IS NULL AND c.collectedAt < :cutoff")
    int markReconciled(@Param("agentId") UUID agentId, @Param("cutoff") Instant cutoff, @Param("lineId") UUID lineId);

    /** UC-16/18: exactly the collections a given set of OfjAgentLines reconciled, so CBS export posts what was actually reconciled rather than everything matching a calendar date. */
    List<Collection> findByReconciledInLineIdIn(List<UUID> lineIds);

    /** UC-11: an agent's collections for a specific calendar day, for the tracking map's route/transaction markers — deliberately date-scoped, unrelated to reconciliation status. */
    List<Collection> findByAgentIdInAndCollectedAtBetween(List<UUID> agentIds, Instant start, Instant end);

    /** UC-11/dashboard: an agent's own recent collections, newest first, for the mobile History/Recent Collections views. */
    List<Collection> findTop50ByAgentIdOrderByCollectedAtDesc(UUID agentId);

    /** UC-09-adjacent: a client's own recent collections, newest first — see CollectionDirectoryService#findRecentByClient. */
    List<Collection> findTop50ByClientIdOrderByCollectedAtDesc(UUID clientId);

    /** Back-Office client transactions export — every collection recorded against this client within an arbitrary [from, to) window. */
    List<Collection> findByClientIdAndCollectedAtBetween(UUID clientId, Instant start, Instant end);
}
