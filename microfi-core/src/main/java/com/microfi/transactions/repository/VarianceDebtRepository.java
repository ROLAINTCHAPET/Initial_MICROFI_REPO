package com.microfi.transactions.repository;

import com.microfi.transactions.domain.VarianceDebt;
import com.microfi.transactions.domain.VarianceDebtStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VarianceDebtRepository extends JpaRepository<VarianceDebt, UUID> {
    Optional<VarianceDebt> findByOfjAgentLineId(UUID ofjAgentLineId);

    List<VarianceDebt> findByAgentIdOrderByCreatedAtDesc(UUID agentId);

    List<VarianceDebt> findByAgentIdAndStatusOrderByCreatedAtDesc(UUID agentId, VarianceDebtStatus status);

    List<VarianceDebt> findByAgentIdInOrderByCreatedAtDesc(List<UUID> agentIds);

    List<VarianceDebt> findByAgentIdInAndStatusOrderByCreatedAtDesc(List<UUID> agentIds, VarianceDebtStatus status);
}
