package com.microfi.transactions.repository;

import com.microfi.transactions.domain.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /** UC-16/18: gathers a branch's collections for the day so they can be posted to the CBS on export. */
    List<Collection> findByAgentIdInAndCollectedAtBetween(List<UUID> agentIds, Instant start, Instant end);

    /** UC-11/dashboard: an agent's own recent collections, newest first, for the mobile History/Recent Collections views. */
    List<Collection> findTop50ByAgentIdOrderByCollectedAtDesc(UUID agentId);

    /** UC-09-adjacent: a client's own recent collections, newest first — see CollectionDirectoryService#findRecentByClient. */
    List<Collection> findTop50ByClientIdOrderByCollectedAtDesc(UUID clientId);
}
