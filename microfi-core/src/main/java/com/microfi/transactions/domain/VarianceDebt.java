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

/** FR-17 — formal shortage recorded against an agent. Immutable audit row (BR-Var-02). */
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
}
