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

    @Query("SELECT COALESCE(SUM(c.amountXaf), 0) FROM Collection c "
            + "WHERE c.agentId = :agentId AND c.collectedAt >= :start AND c.collectedAt < :end")
    long sumAmountByAgentAndWindow(@Param("agentId") UUID agentId, @Param("start") Instant start, @Param("end") Instant end);

    /**
     * UC-16: what OfjService#reconcile actually sums as an agent's digital total — every
     * collection not yet swept into a reconciliation, regardless of which calendar day it was
     * collected on. This is what makes a multi-day-offline agent's backlog reconcilable at all
     * once it finally syncs; {@link #sumAmountByAgentAndWindow} alone would silently never count
     * a collection whose collectedAt has already rolled past "today" by the time it arrives.
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
}
