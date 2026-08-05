package com.microfi.transactions.domain;

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
 * UC-14: a distress alert (architecture.txt core.sos_event, FR-14). Unlike {@link Collection}'s
 * GPS gate (BR-05), an SOS is never blocked for missing/weak GPS — "cannot be disabled by agent"
 * means the server must accept it best-effort, lat/lon included when available.
 * {@code acknowledgedBy}/{@code acknowledgedAt} are set once Back-Office responds; null means
 * still open.
 */
@Entity
@Table(name = "sos_event", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SosEvent {

    @Id
    private UUID id;

    private UUID agentId;

    private Double lat;

    private Double lon;

    private Instant raisedAt;

    private UUID acknowledgedBy;

    private Instant acknowledgedAt;
}
