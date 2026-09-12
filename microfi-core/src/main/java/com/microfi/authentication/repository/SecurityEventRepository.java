package com.microfi.authentication.repository;

import com.microfi.authentication.domain.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID> {
    List<SecurityEvent> findByResolvedAtIsNullOrderByCreatedAtDesc();
    List<SecurityEvent> findByBranchIdAndResolvedAtIsNullOrderByCreatedAtDesc(UUID branchId);
    List<SecurityEvent> findByAgentIdOrderByCreatedAtDesc(UUID agentId);
    List<SecurityEvent> findByAgentIdAndResolvedAtIsNull(UUID agentId);
}
