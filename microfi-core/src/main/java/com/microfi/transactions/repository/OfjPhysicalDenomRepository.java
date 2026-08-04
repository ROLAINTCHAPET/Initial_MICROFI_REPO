package com.microfi.transactions.repository;

import com.microfi.transactions.domain.OfjPhysicalDenom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OfjPhysicalDenomRepository extends JpaRepository<OfjPhysicalDenom, UUID> {
    List<OfjPhysicalDenom> findByOfjAgentLineId(UUID ofjAgentLineId);
    void deleteByOfjAgentLineId(UUID ofjAgentLineId);
}
