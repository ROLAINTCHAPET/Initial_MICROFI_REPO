package com.microfi.transactions.repository;

import com.microfi.transactions.domain.OfjSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfjSessionRepository extends JpaRepository<OfjSession, UUID> {
    /** (branchId, businessDate) is the natural key for the day's session (partial-unique excluding CANCELLED). */
    Optional<OfjSession> findByBranchIdAndBusinessDate(UUID branchId, LocalDate businessDate);

    /** For the Back-Office reports/history screen — past sessions, most recent business date first. */
    List<OfjSession> findByBranchIdOrderByBusinessDateDesc(UUID branchId);

    /** Same history screen, scoped to a chosen period (BR: every export honors the period the user picked). */
    List<OfjSession> findByBranchIdAndBusinessDateBetweenOrderByBusinessDateDesc(UUID branchId, LocalDate from, LocalDate to);
}
