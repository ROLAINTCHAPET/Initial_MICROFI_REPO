package com.microfi.mw.adapters.generic;

import com.microfi.mw.adapters.AbstractCoreBankingAdapter;
import com.microfi.mw.adapters.dto.BalanceResult;
import com.microfi.mw.adapters.dto.CollectionLine;
import com.microfi.mw.adapters.dto.EscrowCreditResult;
import com.microfi.mw.adapters.dto.ExportAckResult;
import com.microfi.mw.adapters.dto.FeeSplitResult;
import com.microfi.mw.adapters.dto.HistoryEntry;
import com.microfi.mw.adapters.dto.MemberVerificationResult;
import com.microfi.mw.adapters.dto.TransactionPostResult;
import com.microfi.mw.adapters.dto.TransactionReversalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * MVP concrete adapter: simulates a Core Banking System so the rest of the platform can be built
 * and demoed against a stable CBS contract before a real vendor (Amplitude, FinanSoft, ...) is
 * wired in. Selected via {@code cbs.vendor=mock}. Backed by {@link MockLedgerEntryRepository} —
 * {@code getBalance}/{@code getHistory} reflect whatever was actually {@code postTransactions}-ed
 * for that member, not a number with no connection to what happened (fabricating a plausible-looking
 * but disconnected balance would defeat the point of simulating a CBS at all).
 */
@Component
@RequiredArgsConstructor
public class MockCbsAdapter extends AbstractCoreBankingAdapter {

    public static final String VENDOR = "mock";

    private final MockLedgerEntryRepository ledgerRepository;

    @Override
    protected String vendorKey() {
        return VENDOR;
    }

    @Override
    public MemberVerificationResult verifyMember(String activationId) {
        if (activationId == null || activationId.isBlank()) {
            return new MemberVerificationResult(false, null, null, "NOT_FOUND");
        }
        String memberId = "CBS-" + Integer.toHexString(activationId.hashCode()).toUpperCase();
        return new MemberVerificationResult(true, memberId, "Mock Member " + memberId, "ACTIVE");
    }

    @Override
    public BalanceResult getBalance(String memberId) {
        return new BalanceResult(memberId, ledgerRepository.sumAmountByMemberId(memberId), Instant.now());
    }

    @Override
    public TransactionPostResult postTransactions(List<CollectionLine> collections) {
        List<String> refs = collections.stream()
                .map(line -> {
                    String reference = "CBSTX-" + line.collectionId();
                    ledgerRepository.save(MockLedgerEntry.builder()
                            .memberId(line.memberId())
                            .amountXaf(line.amountXaf())
                            .reference(reference)
                            .type("DEPOSIT")
                            .postedAt(line.collectedAt())
                            .build());
                    return reference;
                })
                .toList();
        return new TransactionPostResult(true, refs);
    }

    @Override
    public TransactionReversalResult reverseTransaction(String reference) {
        MockLedgerEntry original = ledgerRepository.findByReference(reference).orElse(null);
        if (original == null) {
            return new TransactionReversalResult(false, null);
        }
        String reversalReference = "REV-" + reference;
        // A compensating negative entry, not a delete of the original — the mock's own history
        // (getHistory) must keep showing both the original deposit and its reversal, same as a
        // real CBS statement would, and sumAmountByMemberId nets them out to the correct balance.
        ledgerRepository.save(MockLedgerEntry.builder()
                .memberId(original.getMemberId())
                .amountXaf(-original.getAmountXaf())
                .reference(reversalReference)
                .type("REVERSAL")
                .postedAt(Instant.now())
                .build());
        return new TransactionReversalResult(true, reversalReference);
    }

    @Override
    public FeeSplitResult splitFee(String memberId, String agentId, long amountXaf) {
        long agentCommission = Math.round(amountXaf * 0.30);
        long mfiShare = amountXaf - agentCommission;
        return new FeeSplitResult(agentCommission, mfiShare, newReference("FEESPLIT"));
    }

    @Override
    public EscrowCreditResult creditEscrow(String agentId, long amountXaf, String reference) {
        long pseudoNewBalance = Math.floorMod((long) agentId.hashCode(), 1_000_000L) + amountXaf;
        return new EscrowCreditResult(true, pseudoNewBalance, newReference("ESCROW"));
    }

    @Override
    public List<HistoryEntry> getHistory(String memberId) {
        return ledgerRepository.findByMemberIdOrderByPostedAtDesc(memberId).stream()
                .map(entry -> new HistoryEntry(entry.getReference(), entry.getAmountXaf(), entry.getPostedAt(), entry.getType()))
                .toList();
    }

    @Override
    public ExportAckResult acknowledgeDailyExport(String branchId, String fileUri, String format) {
        log.info("Mock CBS acknowledging daily export for branch {} ({}, {})", branchId, fileUri, format);
        return new ExportAckResult(true, newReference("EXPACK"));
    }
}
