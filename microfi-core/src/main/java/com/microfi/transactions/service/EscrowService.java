package com.microfi.transactions.service;

import com.microfi.transactions.domain.CeilingOverride;
import com.microfi.transactions.domain.EscrowAccount;
import com.microfi.transactions.domain.EscrowLedger;
import com.microfi.transactions.repository.CeilingOverrideRepository;
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
 * The ceiling tracks 1:1 with the escrowed balance (the ceiling *is* the guarantee); a
 * {@link CeilingOverride} lets an administrator temporarily extend it beyond the actual balance.
 * Financial facts are immutable and append-only (no updates/deletes on ledger rows) per the
 * "no soft-deletes on financial facts" design rule.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EscrowService {

    private final EscrowAccountRepository escrowAccountRepository;
    private final EscrowLedgerRepository escrowLedgerRepository;
    private final CeilingOverrideRepository ceilingOverrideRepository;

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

    public EscrowResponse topUp(UUID agentId, long amountXaf, String reference) {
        EscrowAccount account = findAccountOrThrow(agentId);
        account.setBalanceXaf(account.getBalanceXaf() + amountXaf);
        account.setCeilingXaf(account.getCeilingXaf() + amountXaf);
        account.setUpdatedAt(Instant.now());
        escrowAccountRepository.save(account);

        escrowLedgerRepository.save(EscrowLedger.builder()
                .id(UUID.randomUUID())
                .escrowId(account.getId())
                .deltaXaf(amountXaf)
                .reason("TOP_UP")
                .ref(reference)
                .build());

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
                .activeOverrideReason(activeOverride != null ? activeOverride.getReason() : null)
                .overrideValidUntil(activeOverride != null ? activeOverride.getValidUntil() : null)
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
