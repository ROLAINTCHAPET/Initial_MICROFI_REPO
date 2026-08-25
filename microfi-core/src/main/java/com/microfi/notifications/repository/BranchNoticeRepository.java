package com.microfi.notifications.repository;

import com.microfi.notifications.domain.BranchNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BranchNoticeRepository extends JpaRepository<BranchNotice, UUID> {
    List<BranchNotice> findTop10ByBranchIdOrderByCreatedAtDesc(UUID branchId);
}
