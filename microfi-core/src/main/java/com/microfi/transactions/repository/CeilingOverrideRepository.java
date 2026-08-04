package com.microfi.transactions.repository;

import com.microfi.transactions.domain.CeilingOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CeilingOverrideRepository extends JpaRepository<CeilingOverride, UUID> {
    Optional<CeilingOverride> findFirstByAgentIdAndValidUntilAfterOrderByValidUntilDesc(UUID agentId, Instant now);
}
