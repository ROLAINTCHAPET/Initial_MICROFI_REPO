package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.DenominationLine;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.DenominationLineRepository;
import com.microfi.shared.dto.CollectionRequest;
import com.microfi.shared.dto.CollectionResponse;
import com.microfi.shared.dto.DenominationLineDto;
import com.microfi.shared.dto.EscrowResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-06/07/08/12 — the Digital Cash Desk loop: resolve client, enforce the GPS gate, validate the
 * mandatory denomination breakdown, enforce the escrow lockout (FR-04), and record the deposit
 * idempotently so a retried offline sync never double-counts.
 * <p>
 * Takes the agent's id directly (resolved by the caller from the authenticated principal) rather
 * than looking it up itself — {@code transactions} has no business reaching into
 * {@code authentication}'s repository; modules communicate only through public service interfaces.
 * Client existence/status is likewise validated via {@code savings}'s public
 * {@link ClientDirectoryService} rather than a direct repository dependency.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final DenominationLineRepository denominationLineRepository;
    private final ClientDirectoryService clientDirectoryService;
    private final EscrowService escrowService;
    private final ActivationDirectoryService activationDirectoryService;
    private final AgentDirectoryService agentDirectoryService;
    private final GeofenceService geofenceService;
    private final GeocodingService geocodingService;

    @Value("${collection.denomination-threshold-xaf:0}")
    private long denominationThresholdXaf;

    public CollectionResponse recordCollection(UUID agentId, CollectionRequest request) {
        var existing = collectionRepository.findByAgentIdAndDeviceTxId(agentId, request.getDeviceTxId());
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        agentDirectoryService.verifyTransactionPin(agentId, request.getPin());
        requireWithinAssignedGeofence(agentId, request.getLat(), request.getLon());
        clientDirectoryService.requireActiveClient(request.getClientId());
        requireNoPendingActivation(agentId);

        validateDenominationBreakdown(request);
        enforceEscrowCeiling(agentId, request.getAmountXaf());

        Collection collection = Collection.builder()
                .id(UUID.randomUUID())
                .agentId(agentId)
                .clientId(request.getClientId())
                .amountXaf(request.getAmountXaf())
                .lat(request.getLat())
                .lon(request.getLon())
                .accuracyM(request.getAccuracyM())
                .locationName(geocodingService.reverseGeocode(request.getLat(), request.getLon()))
                .collectedAt(request.getCollectedAt())
                .deviceTxId(request.getDeviceTxId())
                .build();
        collectionRepository.save(collection);

        if (request.getDenominationLines() != null) {
            for (DenominationLineDto line : request.getDenominationLines()) {
                denominationLineRepository.save(DenominationLine.builder()
                        .id(UUID.randomUUID())
                        .collectionId(collection.getId())
                        .faceValueXaf(line.getFaceValueXaf())
                        .quantity(line.getQuantity())
                        .build());
            }
        }

        return toResponse(collection, false);
    }

    private void validateDenominationBreakdown(CollectionRequest request) {
        boolean required = request.getAmountXaf() >= denominationThresholdXaf;
        List<DenominationLineDto> lines = request.getDenominationLines();

        if (required && (lines == null || lines.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Denomination breakdown is mandatory for deposits >= " + denominationThresholdXaf + " XAF (BR-02, FR-08)");
        }
        if (lines == null || lines.isEmpty()) {
            return;
        }
        long sum = lines.stream().mapToLong(l -> l.getFaceValueXaf() * l.getQuantity()).sum();
        if (sum != request.getAmountXaf()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Denomination breakdown (" + sum + " XAF) does not match declared amount (" + request.getAmountXaf() + " XAF) (BR-02)");
        }
    }

    /**
     * BR-03: an agent's cumulative cash-in-hand for the day — regular collections plus any
     * agent-collected payments tagged elsewhere (e.g. activation fees, see
     * {@code savings.ActivationPayment}) — must never exceed their effective escrow ceiling.
     * Public so other modules whose agents physically receive cash (not just {@code Collection}
     * rows) can enforce the same lockout before accepting it.
     */
    public void enforceEscrowCeiling(UUID agentId, long amountXaf) {
        EscrowResponse escrow = escrowService.getStatus(agentId);
        Instant startOfDayUtc = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDayUtc = startOfDayUtc.plus(1, ChronoUnit.DAYS);
        long cumulativeToday = collectionRepository.sumAmountByAgentAndWindow(agentId, startOfDayUtc, endOfDayUtc)
                + activationDirectoryService.sumAmountByAgentAndWindow(agentId, startOfDayUtc, endOfDayUtc);

        if (cumulativeToday + amountXaf > escrow.getEffectiveCeilingXaf()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Would exceed escrow ceiling (BR-03): cumulative " + cumulativeToday
                            + " + " + amountXaf + " > ceiling " + escrow.getEffectiveCeilingXaf());
        }
    }

    /**
     * If the agent has a geofence assigned, their captured position must fall inside it — an
     * agent with no geofence assigned is unrestricted (see GeofenceService#isWithinAssignedGeofence).
     * Distinct from BR-05's plain GPS-presence gate (enforced by {@code CollectionRequest}'s
     * {@code @NotNull} lat/lon — a position must exist before this check even runs).
     */
    private void requireWithinAssignedGeofence(UUID agentId, double lat, double lon) {
        if (!geofenceService.isWithinAssignedGeofence(agentId, lat, lon)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are outside your assigned collection zone — move back inside your geofence to collect here");
        }
    }

    /**
     * An agent-registered activation payment isn't a finalized {@code ActivationPayment} (and so
     * isn't counted by {@link #enforceEscrowCeiling}) until the client also confirms it — so while
     * one is pending, the agent's true cash-in-hand is invisible to ceiling accounting. Blocking
     * all new cash intake until it resolves closes that gap instead of trying to estimate it.
     */
    public void requireNoPendingActivation(UUID agentId) {
        if (activationDirectoryService.hasPendingActivation(agentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You have a pending client activation payment awaiting confirmation — resolve it before collecting more cash");
        }
    }

    /** Mobile Home/History views — the agent's own last 50 collections, newest first, with client names resolved. */
    public List<CollectionResponse> findRecentByAgent(UUID agentId) {
        List<Collection> collections = collectionRepository.findTop50ByAgentIdOrderByCollectedAtDesc(agentId);
        Map<UUID, String> namesByClientId = clientDirectoryService.findFullNames(
                collections.stream().map(Collection::getClientId).collect(Collectors.toSet()));

        return collections.stream()
                .map(collection -> CollectionResponse.builder()
                        .id(collection.getId())
                        .agentId(collection.getAgentId())
                        .clientId(collection.getClientId())
                        .clientName(namesByClientId.get(collection.getClientId()))
                        .amountXaf(collection.getAmountXaf())
                        .lat(collection.getLat())
                        .lon(collection.getLon())
                        .accuracyM(collection.getAccuracyM())
                        .locationName(collection.getLocationName())
                        .collectedAt(collection.getCollectedAt())
                        .syncStatus(collection.getSyncStatus())
                        .deviceTxId(collection.getDeviceTxId())
                        .build())
                .toList();
    }

    private CollectionResponse toResponse(Collection collection, boolean duplicate) {
        List<DenominationLineDto> lines = denominationLineRepository.findByCollectionId(collection.getId()).stream()
                .map(line -> {
                    DenominationLineDto dto = new DenominationLineDto();
                    dto.setFaceValueXaf(line.getFaceValueXaf());
                    dto.setQuantity(line.getQuantity());
                    return dto;
                })
                .toList();

        return CollectionResponse.builder()
                .id(collection.getId())
                .agentId(collection.getAgentId())
                .clientId(collection.getClientId())
                .amountXaf(collection.getAmountXaf())
                .lat(collection.getLat())
                .lon(collection.getLon())
                .accuracyM(collection.getAccuracyM())
                .locationName(collection.getLocationName())
                .collectedAt(collection.getCollectedAt())
                .syncStatus(collection.getSyncStatus())
                .deviceTxId(collection.getDeviceTxId())
                .denominationLines(lines)
                .duplicate(duplicate)
                .build();
    }
}
