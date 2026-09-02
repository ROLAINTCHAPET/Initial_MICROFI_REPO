package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.transactions.domain.CeilingOverride;
import com.microfi.transactions.domain.EscrowAccount;
import com.microfi.transactions.domain.EscrowLedger;
import com.microfi.transactions.repository.CeilingOverrideRepository;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.EscrowAccountRepository;
import com.microfi.transactions.repository.EscrowLedgerRepository;
import com.microfi.shared.dto.EscrowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-03/UC-04/UC-05 — escrow wallet top-up, running guarantee and temporary ceiling override.
 * {@code balanceXaf} is the agent's literal security deposit; the collection ceiling it buys is
 * governed by the agent's branch policy (100% = 1:1, the default — see
 * Branch#effectiveDefaultCeilingPct). A {@link CeilingOverride} lets an administrator temporarily
 * extend the ceiling beyond that, independent of the branch policy or the deposit itself.
 * Financial facts are immutable and append-only (no updates/deletes on ledger rows) per the
 * "no soft-deletes on financial facts" design rule. Every top-up requires proof of the cash
 * deposit ({@link EscrowController#topUp} resolves and stores it before calling in here) — the
 * first one is also what activates a PENDING_CEILING agent, since there's no separate "activate"
 * action; registration approval itself never requires this (see RegistrationApplicationService).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EscrowService {

    private final EscrowAccountRepository escrowAccountRepository;
    private final EscrowLedgerRepository escrowLedgerRepository;
    private final CeilingOverrideRepository ceilingOverrideRepository;
    private final CollectionRepository collectionRepository;
    private final ActivationDirectoryService activationDirectoryService;
    private final AgentDirectoryService agentDirectoryService;

    /** Every enrolled agent gets a zero-balance escrow account (UC-04 precondition: "active escrow"). */
    public void createAccountForAgent(UUID agentId) {
        escrowAccountRepository.save(EscrowAccount.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .build());
    }

    public EscrowResponse getStatus(UUID agentId) {
        return toResponse(findAccountOrThrow(agentId));
    }

    /** {@code ledgerEntryId}/{@code proofDocPath} are resolved by the controller before this call — proof storage is reactive (FilePart), this service is not. */
    public EscrowResponse topUp(UUID agentId, long amountXaf, String reference, UUID ledgerEntryId, String proofDocPath) {
        EscrowAccount account = findAccountOrThrow(agentId);
        // balance tracks the literal security deposit 1:1; the ceiling it buys is governed by the
        // agent's branch policy (Branch#effectiveDefaultCeilingPct — 100 = 1:1, the default, so
        // every branch that predates this setting behaves exactly as before).
        int ceilingPct = agentDirectoryService.effectiveCeilingPctForAgent(agentId);
        account.setBalanceXaf(account.getBalanceXaf() + amountXaf);
        account.setCeilingXaf(account.getCeilingXaf() + (amountXaf * ceilingPct / 100));
        account.setUpdatedAt(Instant.now());
        escrowAccountRepository.save(account);

        escrowLedgerRepository.save(EscrowLedger.builder()
                .id(ledgerEntryId)
                .escrowId(account.getId())
                .deltaXaf(amountXaf)
                .reason("TOP_UP")
                .ref(reference)
                .proofDocPath(proofDocPath)
                .build());

        // First funding is what actually makes a newly-enrolled agent usable — until now their
        // ceiling was 0, so every collection attempt would have failed BR-03 anyway even if they
        // could log in. See AgentStatus#PENDING_CEILING.
        if (account.getCeilingXaf() > 0) {
            agentDirectoryService.activateIfPendingCeiling(agentId);
        }

        return toResponse(account);
    }

    public EscrowResponse applyCeilingOverride(UUID agentId, long tempCeilingXaf, String reason, Instant validUntil) {
        EscrowAccount account = findAccountOrThrow(agentId);
        ceilingOverrideRepository.save(CeilingOverride.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .tempCeilingXaf(tempCeilingXaf)
                .reason(reason)
                .validUntil(validUntil)
                .build());
        return toResponse(account);
    }

    private EscrowAccount findAccountOrThrow(UUID agentId) {
        return escrowAccountRepository.findByAgentId(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No escrow account for agent: " + agentId));
    }

    private EscrowResponse toResponse(EscrowAccount account) {
        CeilingOverride activeOverride = ceilingOverrideRepository
                .findFirstByAgentIdAndValidUntilAfterOrderByValidUntilDesc(account.getAgentId(), Instant.now())
                .orElse(null);

        return EscrowResponse.builder()
                .agentId(account.getAgentId())
                .balanceXaf(account.getBalanceXaf())
                .baseCeilingXaf(account.getCeilingXaf())
                .effectiveCeilingXaf(activeOverride != null ? activeOverride.getTempCeilingXaf() : account.getCeilingXaf())
                .cumulativeTodayXaf(cumulativeToday(account.getAgentId()))
                .activeOverrideReason(activeOverride != null ? activeOverride.getReason() : null)
                .overrideValidUntil(activeOverride != null ? activeOverride.getValidUntil() : null)
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    /** Same BR-03 cash-in-hand CollectionService.enforceEscrowCeiling checks against — not yet reconciled, regardless of calendar day (see that method's Javadoc). */
    private long cumulativeToday(UUID agentId) {
        Instant now = Instant.now();
        return collectionRepository.sumUnreconciledByAgent(agentId, now)
                + activationDirectoryService.sumUnreconciled(agentId, now);
    }
}
