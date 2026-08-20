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
    Optional<Agent> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByImei(String imei);
    boolean existsByPhone(String phone);
    boolean existsByEmail(String email);
    List<Agent> findByBranchIdAndStatus(UUID branchId, AgentStatus status);
    List<Agent> findByBranchId(UUID branchId);
}
