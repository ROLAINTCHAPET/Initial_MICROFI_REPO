package com.microfi.transactions.repository;

import com.microfi.transactions.domain.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {

    Optional<Geofence> findByAgentId(UUID agentId);
}
