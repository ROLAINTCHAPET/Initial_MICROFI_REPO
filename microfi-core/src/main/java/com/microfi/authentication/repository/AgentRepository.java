package com.microfi.authentication.repository;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {
    Optional<Agent> findByEmployeeCode(String employeeCode);
    boolean existsByEmployeeCode(String employeeCode);
    boolean existsByImei(String imei);
    List<Agent> findByBranchIdAndStatus(UUID branchId, AgentStatus status);
}
