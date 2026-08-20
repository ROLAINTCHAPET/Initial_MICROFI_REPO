package com.microfi.mw.adapters.generic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MockLedgerEntryRepository extends JpaRepository<MockLedgerEntry, UUID> {

    List<MockLedgerEntry> findByMemberIdOrderByPostedAtDesc(String memberId);

    @Query("SELECT COALESCE(SUM(e.amountXaf), 0) FROM MockLedgerEntry e WHERE e.memberId = :memberId")
    long sumAmountByMemberId(@Param("memberId") String memberId);
}
