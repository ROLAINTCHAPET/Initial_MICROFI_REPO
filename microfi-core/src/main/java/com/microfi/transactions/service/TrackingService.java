package com.microfi.transactions.service;

import com.microfi.shared.dto.LocationPingRequest;
import com.microfi.shared.dto.LocationPingResponse;
import com.microfi.shared.dto.RoutePointDto;
import com.microfi.shared.dto.RouteResponse;
import com.microfi.shared.dto.RouteTransactionDto;
import com.microfi.shared.dto.SosRequest;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.LocationPing;
import com.microfi.transactions.domain.SosEvent;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.LocationPingRepository;
import com.microfi.transactions.repository.SosEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * UC-10 (periodic GPS position reporting), UC-11 (historical route reconstruction), UC-13
 * (geofence breach evaluation, delegated to {@link GeofenceService}) and UC-14 (emergency SOS).
 * Recording a ping/event is otherwise just a durable, immutable log — the mobile client owns the
 * sampling cadence (NFR-09 battery optimisation) and the schedule-window stop (NFR-10).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrackingService {

    private final LocationPingRepository locationPingRepository;
    private final SosEventRepository sosEventRepository;
    private final CollectionRepository collectionRepository;
    private final GeofenceService geofenceService;

    public LocationPingResponse recordLocation(UUID agentId, LocationPingRequest request) {
        LocationPing ping = LocationPing.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .lat(request.getLat())
                .lon(request.getLon())
                .recordedAt(Instant.now())
                .build();
        locationPingRepository.save(ping);
        geofenceService.evaluateLocation(agentId, request.getLat(), request.getLon());

        return LocationPingResponse.builder()
                .id(ping.getId())
                .agentId(agentId)
                .lat(ping.getLat())
                .lon(ping.getLon())
                .recordedAt(ping.getRecordedAt())
                .build();
    }

    /** UC-11: the agent's ordered GPS trail plus that day's collection markers, for the route dashboard. */
    public RouteResponse getRoute(UUID agentId, LocalDate date) {
        Instant startOfDayUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDayUtc = startOfDayUtc.plus(1, ChronoUnit.DAYS);

        List<RoutePointDto> points = locationPingRepository
                .findByAgentIdAndRecordedAtBetweenOrderByRecordedAtAsc(agentId, startOfDayUtc, endOfDayUtc).stream()
                .map(ping -> RoutePointDto.builder().lat(ping.getLat()).lon(ping.getLon()).recordedAt(ping.getRecordedAt()).build())
                .toList();

        List<RouteTransactionDto> transactions = collectionRepository
                .findByAgentIdInAndCollectedAtBetween(List.of(agentId), startOfDayUtc, endOfDayUtc).stream()
                .map(this::toRouteTransaction)
                .toList();

        return RouteResponse.builder()
                .agentId(agentId)
                .date(date)
                .points(points)
                .transactions(transactions)
                .build();
    }

    private RouteTransactionDto toRouteTransaction(Collection collection) {
        return RouteTransactionDto.builder()
                .collectionId(collection.getId())
                .lat(collection.getLat())
                .lon(collection.getLon())
                .locationName(collection.getLocationName())
                .amountXaf(collection.getAmountXaf())
                .collectedAt(collection.getCollectedAt())
                .build();
    }

    public SosResponse raiseSos(UUID agentId, SosRequest request) {
        SosEvent event = SosEvent.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .lat(request.getLat())
                .lon(request.getLon())
                .raisedAt(Instant.now())
                .build();
        sosEventRepository.save(event);

        return toSosResponse(event);
    }

    /**
     * UC-14: Back-Office SOS console. {@code agentIds == null} means unrestricted (ADMIN, global
     * scope); a non-null list scopes to a branch's agents, regardless of their current status —
     * see {@code AgentDirectoryService#findAgentIdsByBranch}.
     */
    public List<SosResponse> listSosEvents(List<UUID> agentIds, boolean unresolvedOnly) {
        List<SosEvent> events;
        if (agentIds == null) {
            events = unresolvedOnly ? sosEventRepository.findByAcknowledgedAtIsNullOrderByRaisedAtDesc()
                    : sosEventRepository.findAllByOrderByRaisedAtDesc();
        } else {
            events = unresolvedOnly ? sosEventRepository.findByAgentIdInAndAcknowledgedAtIsNullOrderByRaisedAtDesc(agentIds)
                    : sosEventRepository.findByAgentIdInOrderByRaisedAtDesc(agentIds);
        }
        return events.stream().map(this::toSosResponse).toList();
    }

    /** Returns the event's agentId so the controller can branch-scope before this is called — see AdminSosController. */
    public UUID findSosEventAgentId(UUID sosEventId) {
        return findSosEventOrThrow(sosEventId).getAgentId();
    }

    public SosResponse acknowledgeSos(UUID sosEventId, UUID acknowledgedByAdminId) {
        SosEvent event = findSosEventOrThrow(sosEventId);
        if (event.getAcknowledgedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SOS event already acknowledged");
        }
        event.setAcknowledgedBy(acknowledgedByAdminId);
        event.setAcknowledgedAt(Instant.now());
        sosEventRepository.save(event);
        return toSosResponse(event);
    }

    private SosEvent findSosEventOrThrow(UUID sosEventId) {
        return sosEventRepository.findById(sosEventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SOS event not found: " + sosEventId));
    }

    private SosResponse toSosResponse(SosEvent event) {
        return SosResponse.builder()
                .id(event.getId())
                .agentId(event.getAgentId())
                .lat(event.getLat())
                .lon(event.getLon())
                .raisedAt(event.getRaisedAt())
                .acknowledgedBy(event.getAcknowledgedBy())
                .acknowledgedAt(event.getAcknowledgedAt())
                .build();
    }
}
