package com.microfi.transactions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Per-agent reconciliation line within an {@link OfjSession}. Delta = physical - digital (BR-01).
 * {@code digitalTotalXaf} is the sum of {@code collectionsTotalXaf} (regular deposits) and
 * {@code activationsTotalXaf} (client booklet activation fees collected in cash) — both cash
 * sources an agent can be holding at end of day, kept as separate fields so the two are visible
 * independently rather than only ever appearing as one merged number.
 */
@Entity
@Table(name = "ofj_agent_line", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfjAgentLine {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ofjId;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private long digitalTotalXaf;

    // Boxed, not primitive: Hibernate infers NOT NULL for primitive long fields regardless of the
    // @Column annotation, and ddl-auto=update can't backfill a NOT NULL ALTER TABLE against a
    // table that already has rows (Postgres rejects it outright). The Java-level @Builder.Default
    // still keeps every row this app writes non-null; only pre-migration rows can read back null,
    // handled as 0 in OfjService#toLineResponse.
    @Builder.Default
    private Long collectionsTotalXaf = 0L;

    @Builder.Default
    private Long activationsTotalXaf = 0L;

    @Column(nullable = false)
    private long physicalTotalXaf;

    @Column(nullable = false)
    private long deltaXaf;
}
