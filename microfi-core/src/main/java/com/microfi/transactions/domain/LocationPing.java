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

/** UC-10: periodic GPS sample for an agent's live field trail (architecture.txt core.location_ping, FR-10). */
@Entity
@Table(name = "location_ping", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationPing {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(nullable = false)
    private Instant recordedAt;
}
