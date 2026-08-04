package com.microfi.transactions.repository;

import com.microfi.transactions.domain.EscrowAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {
    Optional<EscrowAccount> findByAgentId(UUID agentId);
    boolean existsByAgentId(UUID agentId);
}
