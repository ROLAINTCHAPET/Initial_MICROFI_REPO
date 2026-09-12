package com.microfi.authentication.repository;

import com.microfi.authentication.domain.AgentInstallationBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentInstallationBindingRepository extends JpaRepository<AgentInstallationBinding, UUID> {
    Optional<AgentInstallationBinding> findFirstByAgentIdAndSupersededAtIsNull(UUID agentId);
    List<AgentInstallationBinding> findByAgentIdOrderByBoundAtDesc(UUID agentId);
}
