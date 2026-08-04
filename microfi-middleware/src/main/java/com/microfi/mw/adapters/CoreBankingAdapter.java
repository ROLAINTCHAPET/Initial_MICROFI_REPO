package com.microfi.mw.adapters;

import com.microfi.mw.adapters.dto.BalanceResult;
import com.microfi.mw.adapters.dto.CollectionLine;
import com.microfi.mw.adapters.dto.EscrowCreditResult;
import com.microfi.mw.adapters.dto.ExportAckResult;
import com.microfi.mw.adapters.dto.FeeSplitResult;
import com.microfi.mw.adapters.dto.HistoryEntry;
import com.microfi.mw.adapters.dto.MemberVerificationResult;
import com.microfi.mw.adapters.dto.TransactionPostResult;

import java.util.List;

/**
 * Vendor-neutral contract for outbound calls to a Core Banking System (CBS).
 * Concrete implementations map these operations to a specific CBS product
 * (e.g. Amplitude, FinanSoft). The Core Backend never depends on vendor wire formats.
 */
public interface CoreBankingAdapter {

    /** Unique vendor key this adapter implements, matched against the {@code cbs.vendor} property. */
    String vendor();

    MemberVerificationResult verifyMember(String activationId);

    BalanceResult getBalance(String memberId);

    TransactionPostResult postTransactions(List<CollectionLine> collections);

    FeeSplitResult splitFee(String memberId, String agentId, long amountXaf);

    EscrowCreditResult creditEscrow(String agentId, long amountXaf, String reference);

    List<HistoryEntry> getHistory(String memberId);

    ExportAckResult acknowledgeDailyExport(String branchId, String fileUri, String format);
}
