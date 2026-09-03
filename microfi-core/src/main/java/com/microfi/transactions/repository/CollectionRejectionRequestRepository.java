package com.microfi.transactions.repository;

import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectionRejectionRequestRepository extends JpaRepository<CollectionRejectionRequest, UUID> {

    /** BR: a collection can only have one open rejection request at a time — checked before creating a new one. */
    Optional<CollectionRejectionRequest> findByCollectionIdAndStatus(UUID collectionId, CollectionRejectionStatus status);

    List<CollectionRejectionRequest> findByAgentIdInAndStatusOrderByRequestedAtDesc(List<UUID> agentIds, CollectionRejectionStatus status);

    List<CollectionRejectionRequest> findByAgentIdInOrderByRequestedAtDesc(List<UUID> agentIds);

    List<CollectionRejectionRequest> findByAgentIdOrderByRequestedAtDesc(UUID agentId);

    List<CollectionRejectionRequest> findByStatusOrderByRequestedAtDesc(CollectionRejectionStatus status);
}
