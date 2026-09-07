package com.microfi.transactions.repository;

import com.microfi.transactions.domain.EscrowAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {
    Optional<EscrowAccount> findByAgentId(UUID agentId);
    boolean existsByAgentId(UUID agentId);

    /**
     * {@code SELECT ... FOR UPDATE} on one agent's escrow row, used to serialise the BR-03
     * ceiling check (see {@code EscrowService#lockForCeilingCheck}). The row itself is only the
     * lock subject — the check reads a {@code SUM} over collections, which no row lock covers, so
     * concurrent callers have to queue on something they all touch. The escrow account is that
     * something: exactly one row per agent, and already the conceptual owner of the ceiling.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EscrowAccount e where e.agentId = :agentId")
    Optional<EscrowAccount> findByAgentIdForUpdate(@Param("agentId") UUID agentId);
}
