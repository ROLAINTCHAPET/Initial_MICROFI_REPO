package com.microfi.mw.adapters.generic;

import com.microfi.mw.adapters.CoreBankingAdapter;
import com.microfi.mw.adapters.dto.BalanceResult;
import com.microfi.mw.adapters.dto.CollectionLine;
import com.microfi.mw.adapters.dto.EscrowCreditResult;
import com.microfi.mw.adapters.dto.ExportAckResult;
import com.microfi.mw.adapters.dto.FeeSplitResult;
import com.microfi.mw.adapters.dto.HistoryEntry;
import com.microfi.mw.adapters.dto.MemberVerificationResult;
import com.microfi.mw.adapters.dto.TransactionPostResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MVP concrete adapter: simulates a Core Banking System in-memory so the rest of the
 * platform can be built and demoed against a stable, deterministic CBS contract before
 * a real vendor (Amplitude, FinanSoft, ...) is wired in. Selected via {@code cbs.vendor=mock}.
 */
@Component
@Slf4j
public class MockCbsAdapter implements CoreBankingAdapter {

    public static final String VENDOR = "mock";

    @Override
    public String vendor() {
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
        long pseudoBalance = Math.floorMod((long) memberId.hashCode(), 500_000L) + 10_000L;
        return new BalanceResult(memberId, pseudoBalance, Instant.now());
    }

    @Override
    public TransactionPostResult postTransactions(List<CollectionLine> collections) {
        List<String> refs = collections.stream()
                .map(line -> "CBSTX-" + line.collectionId())
                .toList();
        return new TransactionPostResult(true, refs);
    }

    @Override
    public FeeSplitResult splitFee(String memberId, String agentId, long amountXaf) {
        long agentCommission = Math.round(amountXaf * 0.30);
        long mfiShare = amountXaf - agentCommission;
        return new FeeSplitResult(agentCommission, mfiShare, "FEESPLIT-" + UUID.randomUUID());
    }

    @Override
    public EscrowCreditResult creditEscrow(String agentId, long amountXaf, String reference) {
        long pseudoNewBalance = Math.floorMod((long) agentId.hashCode(), 1_000_000L) + amountXaf;
        return new EscrowCreditResult(true, pseudoNewBalance, "ESCROW-" + UUID.randomUUID());
    }

    @Override
    public List<HistoryEntry> getHistory(String memberId) {
        return List.of(
                new HistoryEntry("CBSTX-SAMPLE-1", 5_000L, Instant.now().minusSeconds(86_400), "DEPOSIT"),
                new HistoryEntry("CBSTX-SAMPLE-2", 10_000L, Instant.now().minusSeconds(172_800), "DEPOSIT")
        );
    }

    @Override
    public ExportAckResult acknowledgeDailyExport(String branchId, String fileUri, String format) {
        log.info("Mock CBS acknowledging daily export for branch {} ({}, {})", branchId, fileUri, format);
        return new ExportAckResult(true, "EXPACK-" + UUID.randomUUID());
    }
}
