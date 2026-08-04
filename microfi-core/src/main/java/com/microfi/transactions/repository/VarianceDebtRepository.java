package com.microfi.transactions.repository;

import com.microfi.transactions.domain.VarianceDebt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VarianceDebtRepository extends JpaRepository<VarianceDebt, UUID> {
    Optional<VarianceDebt> findByOfjAgentLineId(UUID ofjAgentLineId);
}
