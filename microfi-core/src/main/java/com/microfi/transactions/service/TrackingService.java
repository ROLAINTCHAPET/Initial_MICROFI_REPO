package com.microfi.transactions.service;

import com.microfi.shared.dto.LocationPingRequest;
import com.microfi.shared.dto.LocationPingResponse;
import com.microfi.shared.dto.SosRequest;
import com.microfi.shared.dto.SosResponse;
import com.microfi.transactions.domain.LocationPing;
import com.microfi.transactions.domain.SosEvent;
import com.microfi.transactions.repository.LocationPingRepository;
import com.microfi.transactions.repository.SosEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-10 (periodic GPS position reporting) and UC-14 (emergency SOS). Both simply persist the
 * sample/event server-side — the mobile client owns the sampling cadence (NFR-09 battery
 * optimisation) and the schedule-window stop (NFR-10), and SMS notification on SOS is FR-09
 * (Fast-Follow, not MVP), so this service's job ends at recording a durable, immutable log.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrackingService {

    private final LocationPingRepository locationPingRepository;
    private final SosEventRepository sosEventRepository;

    public LocationPingResponse recordLocation(UUID agentId, LocationPingRequest request) {
        LocationPing ping = LocationPing.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .lat(request.getLat())
                .lon(request.getLon())
                .recordedAt(Instant.now())
                .build();
        locationPingRepository.save(ping);

        return LocationPingResponse.builder()
                .id(ping.getId())
                .agentId(agentId)
                .lat(ping.getLat())
                .lon(ping.getLon())
                .recordedAt(ping.getRecordedAt())
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

        return SosResponse.builder()
                .id(event.getId())
                .agentId(agentId)
                .lat(event.getLat())
                .lon(event.getLon())
                .raisedAt(event.getRaisedAt())
                .build();
    }
}
