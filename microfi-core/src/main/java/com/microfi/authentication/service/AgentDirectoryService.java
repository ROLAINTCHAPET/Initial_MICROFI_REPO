package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private final PasswordEncoder passwordEncoder;
    private final BranchRepository branchRepository;

    @Value("${agent.transaction-pin.max-failed-attempts:3}")
    private int maxFailedTransactionPinAttempts;

    @Value("${agent.transaction-pin.lockout-minutes:15}")
    private long transactionPinLockoutMinutes;

    public List<UUID> findActiveAgentIdsByBranch(UUID branchId) {
        return agentRepository.findByBranchIdAndStatus(branchId, AgentStatus.ACTIVE).stream()
                .map(Agent::getId)
                .toList();
    }

    /**
     * All agents in the branch regardless of status — unlike {@link #findActiveAgentIdsByBranch},
     * used where hiding a suspended agent's records would be wrong (e.g. an SOS event raised
     * before the agent was suspended must stay visible to the branch's Back-Office console).
     */
    public List<UUID> findAgentIdsByBranch(UUID branchId) {
        return agentRepository.findByBranchId(branchId).stream()
                .map(Agent::getId)
                .toList();
    }

    /** For branch-scoping admin actions taken against a given agent (e.g. cancelling a stuck activation gate). */
    public UUID requireBranchIdForAgent(UUID agentId) {
        return agentRepository.findById(agentId)
                .map(Agent::getBranchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));
    }

    /**
     * How much escrow ceiling a top-up grants per XAF of security deposit for this agent's
     * branch (Branch#effectiveDefaultCeilingPct) — 100 means 1:1 (today's default), used by
     * EscrowService#topUp so it never has to reach into {@code authentication}'s repositories
     * directly.
     */
    public int effectiveCeilingPctForAgent(UUID agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));
        return branchRepository.findById(agent.getBranchId())
                .map(Branch::effectiveDefaultCeilingPct)
                .orElse(Branch.DEFAULT_CEILING_PCT);
    }

    /**
     * UC-16 EOD gate: a branch can't be reconciled before its own configured closing time has
     * passed today, in the branch's own timezone — the day's cash isn't all in yet before then.
     * A branch with no schedule configured (no closeTime/timezone) has no such restriction.
     */
    public boolean isBranchPastCloseTime(UUID branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + branchId));
        ZoneId zone = zoneIdOrNull(branch.getTimezone());
        if (branch.getCloseTime() == null || zone == null) {
            return true;
        }
        LocalTime now = LocalTime.now(zone);
        return !now.isBefore(branch.getCloseTime());
    }

    /**
     * UC-15 schedule-lock: once today's opening time has passed (in the branch's own timezone),
     * it can no longer be rewritten for today — only closeTime stays adjustable, so a branch
     * manager can still shorten or extend the day without being able to retroactively change when
     * the branch is recorded as having opened. A branch with no schedule configured yet (no
     * openTime/timezone) is never locked, since there's nothing on record to protect.
     */
    public boolean isBranchPastOpenTime(Branch branch) {
        ZoneId zone = zoneIdOrNull(branch.getTimezone());
        if (branch.getOpenTime() == null || zone == null) {
            return false;
        }
        LocalTime now = LocalTime.now(zone);
        return !now.isBefore(branch.getOpenTime());
    }

    /**
     * {@code Branch.timezone} is a free-text field with no format validation at write time, so
     * garbage values (e.g. seeded test data) can and do exist — a raw {@code ZoneId.of(...)} call
     * would throw and, worse, would do so mid-stream for a Flux of many branches (as it did for
     * {@code GET /admin/branches} the first time this was missed), silently truncating the
     * response instead of failing one branch cleanly. Treated the same as "no timezone set" by
     * every caller here.
     */
    private ZoneId zoneIdOrNull(String timezone) {
        if (timezone == null) {
            return null;
        }
        try {
            return ZoneId.of(timezone);
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    /** Phone numbers for every agent at a branch, for a branch-wide SMS notice (e.g. a same-day schedule change) — agents with no phone on file are already filtered out. */
    public List<String> findAgentPhonesByBranch(UUID branchId) {
        return agentRepository.findByBranchId(branchId).stream()
                .map(Agent::getPhone)
                .filter(phone -> phone != null && !phone.isBlank())
                .toList();
    }

    /** UC-09 Bluetooth thermal receipt template's "Agent" line (employee code, name, and issuing branch). */
    public AgentReceiptInfo findReceiptInfo(UUID agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));
        String branchName = branchRepository.findById(agent.getBranchId()).map(Branch::getName).orElse("—");
        return new AgentReceiptInfo(agent.getEmployeeCode(), agent.getFullName(), branchName);
    }

    public record AgentReceiptInfo(String employeeCode, String fullName, String branchName) {
    }

    /**
     * UC-16 §6.1: the agent's app self-reports how many collections it currently has queued
     * locally, not yet synced — the server has no other way to know about a collection that
     * hasn't reached it yet. See {@link #hasPendingUnsyncedCollections}.
     */
    public void updateSyncStatus(UUID agentId, int pendingCount) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));
        agent.setPendingSyncCount(pendingCount);
        agentRepository.save(agent);
    }

    /** True if any of these agents currently has an unsynced local queue — blocks OFJ close regardless of reconciliation state. */
    public boolean hasPendingUnsyncedCollections(List<UUID> agentIds) {
        if (agentIds.isEmpty()) {
            return false;
        }
        return agentRepository.findAllById(agentIds).stream()
                .anyMatch(agent -> agent.getPendingSyncCount() != null && agent.getPendingSyncCount() > 0);
    }

    /**
     * UC-06 transaction confirmation: every collection must be confirmed with the agent's own
     * transaction PIN, checked independently from login (which authenticates with the agent's
     * password instead — see {@code AuthenticationController}). Locks out after repeated
     * failures, mirroring the login lockout mechanism but tracked on its own counter, since a
     * short numeric secret checked on every transaction deserves its own brute-force guard.
     */
    public void verifyTransactionPin(UUID agentId, String pin) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found: " + agentId));

        // A PENDING_CEILING agent can log in and use the app (AuthenticationController#login) but
        // can't collect until an admin funds their escrow account — collecting with a zero ceiling
        // would fail BR-03 anyway, but this gives a direct, unambiguous reason instead of a
        // confusing "exceeds ceiling" message on an agent who was never meant to transact yet.
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your account is awaiting setup — ask your branch to fund your escrow account before you can collect");
        }
        if (Boolean.TRUE.equals(agent.getPinMustChange())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Set your own transaction PIN before collecting (PATCH /agents/me/pin)");
        }
        if (agent.getTransactionPinLockedUntil() != null && agent.getTransactionPinLockedUntil().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "Too many failed PIN attempts. Try again after " + DateTimeFormatter.ISO_INSTANT.format(agent.getTransactionPinLockedUntil()));
        }
        if (!passwordEncoder.matches(pin, agent.getPinHash())) {
            int attempts = (agent.getFailedTransactionPinAttempts() == null ? 0 : agent.getFailedTransactionPinAttempts()) + 1;
            agent.setFailedTransactionPinAttempts(attempts);
            if (attempts >= maxFailedTransactionPinAttempts) {
                agent.setTransactionPinLockedUntil(Instant.now().plus(transactionPinLockoutMinutes, ChronoUnit.MINUTES));
            }
            agentRepository.save(agent);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect PIN");
        }
        if ((agent.getFailedTransactionPinAttempts() != null && agent.getFailedTransactionPinAttempts() > 0) || agent.getTransactionPinLockedUntil() != null) {
            agent.setFailedTransactionPinAttempts(0);
            agent.setTransactionPinLockedUntil(null);
            agentRepository.save(agent);
        }
    }

    /** Called by EscrowService#topUp once an agent's escrow ceiling actually becomes funded — the only way a PENDING_CEILING agent becomes usable. No-op for any other status (e.g. a SUSPENDED agent stays suspended regardless of top-ups). */
    public void activateIfPendingCeiling(UUID agentId) {
        agentRepository.findById(agentId).ifPresent(agent -> {
            if (agent.getStatus() == AgentStatus.PENDING_CEILING) {
                agent.setStatus(AgentStatus.ACTIVE);
                agentRepository.save(agent);
            }
        });
    }
}
