package com.microfi.transactions.repository;

import com.microfi.transactions.domain.AgentMisconductReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentMisconductReportRepository extends JpaRepository<AgentMisconductReport, UUID> {

    List<AgentMisconductReport> findAllByOrderByReportedAtDesc();

    List<AgentMisconductReport> findByAgentIdInOrderByReportedAtDesc(List<UUID> agentIds);

    List<AgentMisconductReport> findByReviewedAtIsNullOrderByReportedAtDesc();

    List<AgentMisconductReport> findByAgentIdInAndReviewedAtIsNullOrderByReportedAtDesc(List<UUID> agentIds);
}
