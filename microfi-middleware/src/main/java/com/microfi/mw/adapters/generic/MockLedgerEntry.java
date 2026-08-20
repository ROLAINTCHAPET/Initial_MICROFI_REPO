package com.microfi.mw.adapters.generic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * {@link MockCbsAdapter}'s own simulated ledger — deliberately not part of the shared
 * {@code com.microfi.mw.domain} package, since a real vendor adapter (Amplitude, FinanSoft) would
 * never need this; it exists purely so the mock behaves like an actual CBS (balance and history
 * reflect what was really posted) instead of returning numbers with no connection to what
 * happened, which is what {@code getBalance}/{@code getHistory} did before this existed.
 */
@Entity
@Table(name = "mock_ledger_entry", schema = "mw")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String memberId;

    @Column(nullable = false)
    private long amountXaf;

    @Column(nullable = false)
    private String reference;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Instant postedAt;
}
