package com.microfi.notifications.repository;

import com.microfi.notifications.domain.BroadcastAudience;
import com.microfi.notifications.domain.BroadcastMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BroadcastMessageRepository extends JpaRepository<BroadcastMessage, UUID> {

    /** A recipient's own poll: every network-wide (branchId IS NULL) message for this audience, plus their own branch's. */
    @Query("SELECT b FROM BroadcastMessage b WHERE b.audience = :audience AND (b.branchId IS NULL OR b.branchId = :branchId) ORDER BY b.createdAt DESC")
    List<BroadcastMessage> findRecentForAudienceAndBranch(@Param("audience") BroadcastAudience audience, @Param("branchId") UUID branchId, Pageable pageable);

    List<BroadcastMessage> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<BroadcastMessage> findByBranchIdOrderByCreatedAtDesc(UUID branchId, Pageable pageable);
}
