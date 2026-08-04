package com.microfi.mw.service;

import com.microfi.mw.adapters.CbsAdapterFactory;
import com.microfi.mw.adapters.CoreBankingAdapter;
import com.microfi.mw.adapters.dto.BalanceResult;
import com.microfi.mw.adapters.dto.CollectionLine;
import com.microfi.mw.adapters.dto.EscrowCreditResult;
import com.microfi.mw.adapters.dto.ExportAckResult;
import com.microfi.mw.adapters.dto.FeeSplitResult;
import com.microfi.mw.adapters.dto.HistoryEntry;
import com.microfi.mw.adapters.dto.MemberVerificationResult;
import com.microfi.mw.adapters.dto.TransactionPostResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Backs the {@code /mw/v1} controllers: routes every outbound CBS operation through the
 * active vendor adapter, audits it via {@link CbsCallLogger}, and guards money-moving
 * operations with {@link IdempotencyService}.
 */
@Service
@RequiredArgsConstructor
public class CbsIntegrationService {

    private final CbsAdapterFactory adapterFactory;
    private final CbsCallLogger callLogger;
    private final IdempotencyService idempotencyService;

    public MemberVerificationResult verifyMember(String correlationId, String activationId) {
        CoreBankingAdapter adapter = adapterFactory.getActiveAdapter();
        return callLogger.logged(correlationId, "members.verify", adapter.vendor(),
                () -> adapter.verifyMember(activationId));
    }

    public BalanceResult getBalance(String correlationId, String memberId) {
        CoreBankingAdapter adapter = adapterFactory.getActiveAdapter();
        return callLogger.logged(correlationId, "members.balance", adapter.vendor(),
                () -> adapter.getBalance(memberId));
    }

    public TransactionPostResult postTransactions(String correlationId, String idempotencyKey, List<CollectionLine> collections) {
        CoreBankingAdapter adapter = adapterFactory.getActiveAdapter();
        return idempotencyService.executeIdempotent(idempotencyKey, "transactions.post", collections, TransactionPostResult.class,
                () -> callLogger.logged(correlationId, "transactions.post", adapter.vendor(),
                        () -> adapter.postTransactions(collections)));
    }

    public FeeSplitResult splitFee(String correlationId, String idempotencyKey, String memberId, String agentId, long amountXaf) {
        CoreBankingAdapter adapter = adapterFactory.getActiveAdapter();
        record Request(String memberId, String agentId, long amountXaf) {
        }
        return idempotencyService.executeIdempotent(idempotencyKey, "fees.split", new Request(memberId, agentId, amountXaf), FeeSplitResult.class,
                () -> callLogger.logged(correlationId, "fees.split", adapter.vendor(),
                        () -> adapter.splitFee(memberId, agentId, amountXaf)));
    }

    public EscrowCreditResult creditEscrow(String correlationId, String idempotencyKey, String agentId, long amountXaf, String reference) {
        CoreBankingAdapter adapter = adapterFactory.getActiveAdapter();
        record Request(String agentId, long amountXaf, String reference) {
        }
        return idempotencyService.executeIdempotent(idempotencyKey, "escrow.credit", new Request(agentId, amountXaf, reference), EscrowCreditResult.class,
                () -> callLogger.logged(correlationId, "escrow.credit", adapter.vendor(),
                        () -> adapter.creditEscrow(agentId, amountXaf, reference)));
    }

    public List<HistoryEntry> getHistory(String correlationId, String memberId) {
        CoreBankingAdapter adapter = adapterFactory.getActiveAdapter();
        return callLogger.logged(correlationId, "members.history", adapter.vendor(),
                () -> adapter.getHistory(memberId));
    }

    public ExportAckResult acknowledgeDailyExport(String correlationId, String branchId, String fileUri, String format) {
        CoreBankingAdapter adapter = adapterFactory.getActiveAdapter();
        return callLogger.logged(correlationId, "exports.daily", adapter.vendor(),
                () -> adapter.acknowledgeDailyExport(branchId, fileUri, format));
    }
}
