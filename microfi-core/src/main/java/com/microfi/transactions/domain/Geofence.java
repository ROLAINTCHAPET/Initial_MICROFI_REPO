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
 * UC-13/BR-Fence-01: an agent's assigned market/district perimeter (architecture.txt
 * core.geofence). One geofence per agent — the doc allows "per agent or per route", but this MVP
 * only supports per-agent (a route-level geofence would need a route/schedule concept this
 * project doesn't have yet).
 * <p>
 * {@code verticesCsv} is a simplified encoding of the polygon — {@code "lat,lon;lat,lon;..."} —
 * rather than a PostGIS {@code geography} column (the doc's suggested type): this project has no
 * PostGIS dependency anywhere else, and the point-in-polygon check {@link GeofenceService} does
 * is simple enough not to need it. At least 3 vertices are required to form a polygon.
 */
@Entity
@Table(name = "geofence", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Geofence {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID agentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String verticesCsv;
}
