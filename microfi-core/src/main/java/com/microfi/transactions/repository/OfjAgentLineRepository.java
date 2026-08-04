package com.microfi.transactions.repository;

import com.microfi.transactions.domain.OfjAgentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfjAgentLineRepository extends JpaRepository<OfjAgentLine, UUID> {
    Optional<OfjAgentLine> findByOfjIdAndAgentId(UUID ofjId, UUID agentId);
    List<OfjAgentLine> findByOfjId(UUID ofjId);
}
