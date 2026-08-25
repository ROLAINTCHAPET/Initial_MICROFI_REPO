package com.microfi.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit trail of escrow balance movements (top-ups, adjustments). No updates, no deletes. */
@Entity
@Table(name = "escrow_ledger", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowLedger {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID escrowId;

    @Column(nullable = false)
    private long deltaXaf;

    @Column(nullable = false)
    private String reason;

    private String ref;

    /** Proof of the cash deposit (receipt, deposit slip, counted-cash acknowledgment) — always set by EscrowService#topUp; nullable at the schema level only because pre-existing rows predate this requirement. */
    private String proofDocPath;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
