package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.shared.dto.GeofenceAlertResponse;
import com.microfi.shared.dto.GeofenceRequest;
import com.microfi.shared.dto.GeofenceResponse;
import com.microfi.shared.dto.GeofenceVertexDto;
import com.microfi.transactions.domain.Geofence;
import com.microfi.transactions.domain.GeofenceAlert;
import com.microfi.transactions.repository.GeofenceAlertRepository;
import com.microfi.transactions.repository.GeofenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * UC-13 — Real-Time Geofence Breach Alert. {@link #evaluateLocation} is called from
 * {@link TrackingService} after every GPS ping and runs a simple point-in-polygon check
 * (ray-casting; see class javadoc on {@link Geofence} for why this isn't PostGIS). An agent must
 * be outside continuously for {@code geofence.grace-period-seconds} (UC-13 A1, default 120s)
 * before a breach becomes a visible alert — a single stray/inaccurate ping should never page a
 * manager — and returning inside auto-resolves it (UC-13 A2) or, if it never cleared the grace
 * period, discards it entirely since it was never actually an alert.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GeofenceService {

    private final GeofenceRepository geofenceRepository;
    private final GeofenceAlertRepository geofenceAlertRepository;
    private final AgentDirectoryService agentDirectoryService;

    @Value("${geofence.grace-period-seconds:120}")
    private long gracePeriodSeconds;

    /**
     * Collection-time gate (distinct from {@link #evaluateLocation}'s async alerting): an agent
     * with no geofence assigned is unrestricted (true); one with a geofence must be inside it.
     * Read-only — never touches {@link GeofenceAlert} state.
     */
    public boolean isWithinAssignedGeofence(UUID agentId, double lat, double lon) {
        return geofenceRepository.findByAgentId(agentId)
                .map(geofence -> isInsidePolygon(lat, lon, parseVertexPairs(geofence.getVerticesCsv())))
                .orElse(true);
    }

    public void evaluateLocation(UUID agentId, double lat, double lon) {
        Optional<Geofence> geofenceOpt = geofenceRepository.findByAgentId(agentId);
        if (geofenceOpt.isEmpty()) {
            return;
        }
        Geofence geofence = geofenceOpt.get();
        boolean inside = isInsidePolygon(lat, lon, parseVertexPairs(geofence.getVerticesCsv()));
        Optional<GeofenceAlert> openBreach = geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId);

        if (inside) {
            openBreach.ifPresent(breach -> {
                if (breach.getRaisedAt() != null) {
                    breach.setResolvedAt(Instant.now());
                    geofenceAlertRepository.save(breach);
                } else {
                    geofenceAlertRepository.delete(breach);
                }
            });
            return;
        }

        Instant now = Instant.now();
        if (openBreach.isEmpty()) {
            geofenceAlertRepository.save(GeofenceAlert.builder()
                    .id(UUID.randomUUID())
                    .agentId(agentId)
                    .geofenceId(geofence.getId())
                    .firstDetectedOutsideAt(now)
                    .build());
            return;
        }

        GeofenceAlert breach = openBreach.get();
        if (breach.getRaisedAt() == null
                && Duration.between(breach.getFirstDetectedOutsideAt(), now).getSeconds() >= gracePeriodSeconds) {
            breach.setRaisedAt(now);
            geofenceAlertRepository.save(breach);
        }
    }

    public GeofenceResponse setGeofence(UUID agentId, GeofenceRequest request) {
        String csv = request.getVertices().stream()
                .map(v -> v.getLat() + "," + v.getLon())
                .collect(Collectors.joining(";"));
        Geofence geofence = geofenceRepository.findByAgentId(agentId)
                .orElseGet(() -> Geofence.builder().id(UUID.randomUUID()).agentId(agentId).build());
        geofence.setVerticesCsv(csv);
        geofenceRepository.save(geofence);
        return toResponse(geofence);
    }

    /**
     * Bulk convenience for a branch manager/admin who doesn't want to draw the same perimeter
     * one agent at a time: writes {@code request}'s vertices as every currently-active agent's
     * own {@link Geofence} row (same as calling {@link #setGeofence} once per agent). There is no
     * shared "branch geofence" concept — an agent added to the branch afterward still needs their
     * own geofence set, same as today. Returns how many agents were updated.
     */
    public int applyGeofenceToBranch(UUID branchId, GeofenceRequest request) {
        List<UUID> agentIds = agentDirectoryService.findActiveAgentIdsByBranch(branchId);
        agentIds.forEach(agentId -> setGeofence(agentId, request));
        return agentIds.size();
    }

    public GeofenceResponse getGeofence(UUID agentId) {
        Geofence geofence = geofenceRepository.findByAgentId(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No geofence assigned to agent: " + agentId));
        return toResponse(geofence);
    }

    /**
     * Returns the agent to the same unrestricted state they were in before any geofence was ever
     * set (see {@link #isWithinAssignedGeofence}'s {@code orElse(true)}) — not a new state this
     * codebase needs to guard against, just the pre-existing default. Idempotent: deleting an
     * already-absent geofence is a no-op rather than a 404, matching normal DELETE semantics.
     * <p>
     * Any still-open breach alert for this agent is closed the same way {@link #evaluateLocation}
     * closes one when the agent returns inside — raised alerts get {@code resolvedAt} stamped
     * (kept for BR-Fence-02's audit trail), an alert still within its grace period (never
     * actually surfaced) is simply discarded — otherwise it would sit open forever, since nothing
     * will ever call evaluateLocation for this agent's now-deleted geofence again.
     */
    public void deleteGeofence(UUID agentId) {
        geofenceRepository.findByAgentId(agentId).ifPresent(geofence -> {
            geofenceAlertRepository.findByAgentIdAndResolvedAtIsNull(agentId).ifPresent(breach -> {
                if (breach.getRaisedAt() != null) {
                    breach.setResolvedAt(Instant.now());
                    geofenceAlertRepository.save(breach);
                } else {
                    geofenceAlertRepository.delete(breach);
                }
            });
            geofenceRepository.delete(geofence);
        });
    }

    /**
     * Bulk convenience mirroring {@link #applyGeofenceToBranch} — clears every currently-active
     * agent's own geofence (there being no shared branch-level row to begin with). Returns how
     * many agents actually had one to clear.
     */
    public int clearGeofenceFromBranch(UUID branchId) {
        List<UUID> agentIds = agentDirectoryService.findActiveAgentIdsByBranch(branchId);
        int cleared = 0;
        for (UUID agentId : agentIds) {
            if (geofenceRepository.findByAgentId(agentId).isPresent()) {
                deleteGeofence(agentId);
                cleared++;
            }
        }
        return cleared;
    }

    public List<GeofenceAlertResponse> listAlerts(UUID agentId) {
        return geofenceAlertRepository.findByAgentIdOrderByFirstDetectedOutsideAtDesc(agentId).stream()
                .map(this::toAlertResponse)
                .toList();
    }

    /** Standard ray-casting point-in-polygon test; treats lat/lon as planar x/y, adequate at market/district scale. */
    private boolean isInsidePolygon(double lat, double lon, List<double[]> vertices) {
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = vertices.get(i)[0], lonI = vertices.get(i)[1];
            double latJ = vertices.get(j)[0], lonJ = vertices.get(j)[1];
            boolean edgeCrossesRay = ((lonI > lon) != (lonJ > lon))
                    && (lat < (latJ - latI) * (lon - lonI) / (lonJ - lonI) + latI);
            if (edgeCrossesRay) {
                inside = !inside;
            }
        }
        return inside;
    }

    private List<double[]> parseVertexPairs(String verticesCsv) {
        return Stream.of(verticesCsv.split(";"))
                .map(pair -> {
                    String[] parts = pair.split(",");
                    return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
                })
                .toList();
    }

    private GeofenceResponse toResponse(Geofence geofence) {
        List<GeofenceVertexDto> vertices = Arrays.stream(geofence.getVerticesCsv().split(";"))
                .map(pair -> {
                    String[] parts = pair.split(",");
                    GeofenceVertexDto dto = new GeofenceVertexDto();
                    dto.setLat(Double.parseDouble(parts[0]));
                    dto.setLon(Double.parseDouble(parts[1]));
                    return dto;
                })
                .toList();
        return GeofenceResponse.builder().agentId(geofence.getAgentId()).vertices(vertices).build();
    }

    private GeofenceAlertResponse toAlertResponse(GeofenceAlert alert) {
        return GeofenceAlertResponse.builder()
                .id(alert.getId())
                .agentId(alert.getAgentId())
                .firstDetectedOutsideAt(alert.getFirstDetectedOutsideAt())
                .raisedAt(alert.getRaisedAt())
                .resolvedAt(alert.getResolvedAt())
                .active(alert.getRaisedAt() != null && alert.getResolvedAt() == null)
                .build();
    }
}
