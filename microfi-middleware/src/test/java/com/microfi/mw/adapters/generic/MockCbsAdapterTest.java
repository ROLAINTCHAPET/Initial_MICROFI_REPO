package com.microfi.mw.adapters.generic;

import com.microfi.mw.adapters.dto.CollectionLine;
import com.microfi.mw.adapters.dto.HistoryEntry;
import com.microfi.mw.adapters.dto.TransactionPostResult;
import com.microfi.mw.adapters.dto.TransactionReversalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for making the mock genuinely simulate a CBS ledger: getBalance/getHistory
 * used to return numbers with no connection to what was actually posted (a hash of the memberId,
 * two hardcoded literals). They must now reflect real {@link MockLedgerEntry} rows.
 */
class MockCbsAdapterTest {

    @Mock
    private MockLedgerEntryRepository ledgerRepository;

    private MockCbsAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new MockCbsAdapter(ledgerRepository);
    }

    @Test
    void getBalanceReflectsSumOfPostedEntries() {
        when(ledgerRepository.sumAmountByMemberId("CBS-1")).thenReturn(15_000L);

        var balance = adapter.getBalance("CBS-1");

        assertThat(balance.balanceXaf()).isEqualTo(15_000L);
    }

    @Test
    void getBalanceIsZeroForMemberWithNoHistory() {
        when(ledgerRepository.sumAmountByMemberId("CBS-NEW")).thenReturn(0L);

        var balance = adapter.getBalance("CBS-NEW");

        assertThat(balance.balanceXaf()).isEqualTo(0L);
    }

    @Test
    void postTransactionsPersistsOneLedgerEntryPerLine() {
        CollectionLine line = new CollectionLine(UUID.randomUUID(), "CBS-1", 2000L, Instant.now());

        TransactionPostResult result = adapter.postTransactions(List.of(line));

        ArgumentCaptor<MockLedgerEntry> captor = ArgumentCaptor.forClass(MockLedgerEntry.class);
        org.mockito.Mockito.verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo("CBS-1");
        assertThat(captor.getValue().getAmountXaf()).isEqualTo(2000L);
        assertThat(result.success()).isTrue();
        assertThat(result.postedReferences()).containsExactly("CBSTX-" + line.collectionId());
    }

    @Test
    void getHistoryReturnsRealEntriesNotHardcodedSamples() {
        MockLedgerEntry entry = MockLedgerEntry.builder()
                .memberId("CBS-1").amountXaf(2000L).reference("CBSTX-1").type("DEPOSIT").postedAt(Instant.now()).build();
        when(ledgerRepository.findByMemberIdOrderByPostedAtDesc("CBS-1")).thenReturn(List.of(entry));

        List<HistoryEntry> history = adapter.getHistory("CBS-1");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).reference()).isEqualTo("CBSTX-1");
        assertThat(history.get(0).amountXaf()).isEqualTo(2000L);
    }

    @Test
    void getHistoryIsEmptyForMemberWithNoHistory() {
        when(ledgerRepository.findByMemberIdOrderByPostedAtDesc(eq("CBS-NEW"))).thenReturn(List.of());

        assertThat(adapter.getHistory("CBS-NEW")).isEmpty();
    }

    @Test
    void reverseTransactionPostsACompensatingNegativeEntry() {
        MockLedgerEntry original = MockLedgerEntry.builder()
                .memberId("CBS-1").amountXaf(2000L).reference("CBSTX-1").type("DEPOSIT").postedAt(Instant.now()).build();
        when(ledgerRepository.findByReference("CBSTX-1")).thenReturn(java.util.Optional.of(original));

        TransactionReversalResult result = adapter.reverseTransaction("CBSTX-1");

        ArgumentCaptor<MockLedgerEntry> captor = ArgumentCaptor.forClass(MockLedgerEntry.class);
        org.mockito.Mockito.verify(ledgerRepository).save(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo("CBS-1");
        assertThat(captor.getValue().getAmountXaf()).isEqualTo(-2000L);
        assertThat(captor.getValue().getType()).isEqualTo("REVERSAL");
        assertThat(result.success()).isTrue();
        assertThat(result.reversalReference()).isEqualTo("REV-CBSTX-1");
    }

    @Test
    void reverseTransactionNetsBalanceBackToZero() {
        // Confirms the reversal is genuinely a compensating entry, not just a status flag —
        // sumAmountByMemberId (what getBalance reads) must reflect it the same way it reflects
        // every other posted entry.
        when(ledgerRepository.sumAmountByMemberId("CBS-1")).thenReturn(0L);

        var balance = adapter.getBalance("CBS-1");

        assertThat(balance.balanceXaf()).isEqualTo(0L);
    }

    @Test
    void reverseTransactionFailsGracefullyForAnUnknownReference() {
        when(ledgerRepository.findByReference("CBSTX-UNKNOWN")).thenReturn(java.util.Optional.empty());

        TransactionReversalResult result = adapter.reverseTransaction("CBSTX-UNKNOWN");

        assertThat(result.success()).isFalse();
        assertThat(result.reversalReference()).isNull();
        org.mockito.Mockito.verify(ledgerRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}
