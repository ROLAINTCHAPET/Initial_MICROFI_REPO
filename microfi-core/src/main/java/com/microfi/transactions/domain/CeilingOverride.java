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

/** FR-05 temporary, audited increase of an agent's collection ceiling. */
@Entity
@Table(name = "ceiling_override", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CeilingOverride {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private long tempCeilingXaf;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant validUntil;

    private String approvedBy;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
