package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public service interface exposing the minimal agent-directory facts other modules legitimately
 * need (e.g. "which active agents belong to this branch", for {@code transactions}' OFJ session
 * close check) without giving them direct access to {@link AgentRepository}.
 */
@Service
@RequiredArgsConstructor
public class AgentDirectoryService {

    private final AgentRepository agentRepository;

    public List<UUID> findActiveAgentIdsByBranch(UUID branchId) {
        return agentRepository.findByBranchIdAndStatus(branchId, AgentStatus.ACTIVE).stream()
                .map(Agent::getId)
                .toList();
    }
}
