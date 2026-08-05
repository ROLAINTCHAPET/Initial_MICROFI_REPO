package com.microfi.savings.repository;

import com.microfi.savings.domain.ActivationRequest;
import com.microfi.savings.domain.ActivationRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {

    Optional<ActivationRequest> findByClientIdAndStatus(UUID clientId, ActivationRequestStatus status);

    boolean existsByAgentIdAndStatus(UUID agentId, ActivationRequestStatus status);

    List<ActivationRequest> findByAgentIdAndStatus(UUID agentId, ActivationRequestStatus status);

    List<ActivationRequest> findByStatusAndCreatedAtBefore(ActivationRequestStatus status, Instant cutoff);
}
