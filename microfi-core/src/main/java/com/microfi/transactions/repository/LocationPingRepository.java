package com.microfi.transactions.repository;

import com.microfi.transactions.domain.LocationPing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LocationPingRepository extends JpaRepository<LocationPing, UUID> {

    /** UC-11: an agent's ordered GPS trail for a given day, for the route-mapping dashboard. */
    List<LocationPing> findByAgentIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID agentId, Instant start, Instant end);
}
