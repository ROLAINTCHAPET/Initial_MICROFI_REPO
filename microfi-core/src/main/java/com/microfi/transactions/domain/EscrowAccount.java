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

@Entity
@Table(name = "escrow_account", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID agentId;

    @Builder.Default
    private long balanceXaf = 0;

    @Builder.Default
    private long ceilingXaf = 0;

    @Builder.Default
    private Instant updatedAt = Instant.now();
}
