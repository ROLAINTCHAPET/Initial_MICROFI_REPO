package com.microfi.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * FR-17 — formal shortage recorded against an agent. The original recording is an immutable audit
 * row (BR-Var-02) — {@code agentId}/{@code ofjAgentLineId}/{@code amountXaf}/{@code createdAt}
 * never change after creation. A write-off (VarianceDebtController#writeOff) doesn't violate that:
 * it's a status transition with its own audit trail (reason, proof, who, when), not an edit or
 * delete of the original shortage record.
 */
@Entity
@Table(name = "variance_debt", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianceDebt {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private UUID ofjAgentLineId;

    @Column(nullable = false)
    private long amountXaf;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VarianceDebtStatus status = VarianceDebtStatus.OPEN;

    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Populated only when {@link #status} is {@link VarianceDebtStatus#WRITTEN_OFF}. */
    private String writtenOffReason;
    private String writtenOffProofPath;
    private UUID writtenOffBy;
    private Instant writtenOffAt;
}
