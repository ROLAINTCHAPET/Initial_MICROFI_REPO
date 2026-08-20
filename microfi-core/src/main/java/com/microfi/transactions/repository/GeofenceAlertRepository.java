package com.microfi.transactions.repository;

import com.microfi.transactions.domain.GeofenceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeofenceAlertRepository extends JpaRepository<GeofenceAlert, UUID> {

    /** The breach currently in progress for this agent (raised or still within grace period), if any. */
    Optional<GeofenceAlert> findByAgentIdAndResolvedAtIsNull(UUID agentId);

    List<GeofenceAlert> findByAgentIdOrderByFirstDetectedOutsideAtDesc(UUID agentId);

    List<GeofenceAlert> findByAgentIdAndRaisedAtIsNotNullAndResolvedAtIsNullOrderByRaisedAtDesc(UUID agentId);
}
