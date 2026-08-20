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
 * UC-13/BR-Fence-02: a geofence breach, retained for audit even after resolution
 * (architecture.txt core.geofence_alert). {@code firstDetectedOutsideAt} tracks when the agent
 * was first seen outside the polygon — {@code raisedAt} stays null until the configurable grace
 * period (UC-13 A1, e.g. 2 minutes) elapses, so a brief excursion never becomes a visible alert.
 * {@code resolvedAt} is set automatically once the agent is seen back inside (UC-13 A2).
 */
@Entity
@Table(name = "geofence_alert", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceAlert {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private UUID geofenceId;

    @Column(nullable = false)
    private Instant firstDetectedOutsideAt;

    private Instant raisedAt;

    private Instant resolvedAt;
}
