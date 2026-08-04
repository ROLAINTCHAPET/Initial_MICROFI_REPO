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

/**
 * A single cash deposit collected by an agent. Offline-safe: {@code (agentId, deviceTxId)} is
 * unique so a retried offline sync never double-counts. lat/lon are mandatory (BR-05, FR-12) —
 * the server is the final authority on the GPS gate, not just the mobile client.
 */
@Entity
@Table(name = "collection", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Collection {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private long amountXaf;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    private Float accuracyM;

    @Column(nullable = false)
    private Instant collectedAt;

    @Column(nullable = false)
    @Builder.Default
    private String syncStatus = "SYNCED";

    @Column(nullable = false)
    private String deviceTxId;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
