package com.microfi.transactions.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.events.CollectionGeocodeEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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
    private final ApplicationEventPublisher applicationEventPublisher;
    private final AuditService auditService;

    @Value("${collection.denomination-threshold-xaf:0}")
    private long denominationThresholdXaf;

    public CollectionResponse recordCollection(UUID agentId, CollectionRequest request) {
        var existing = collectionRepository.findByAgentIdAndDeviceTxId(agentId, request.getDeviceTxId());
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        agentDirectoryService.verifyTransactionPin(agentId, request.getPin());
        agentDirectoryService.requireWithinScheduleWindow(agentId, request.getCollectedAt());
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
                // locationName starts null and is filled in asynchronously — see
                // CollectionGeocodeListener. Was previously resolved synchronously right here via
                // GeocodingService.reverseGeocode, a real blocking call to OpenStreetMap's
                // rate-limited free Nominatim service; a burst of agents reconnecting together
                // meant a burst of Core threads each held open for up to Nominatim's own timeout,
                // for a field that was already best-effort/nullable on any lookup failure anyway.
                .collectedAt(request.getCollectedAt())
                .deviceTxId(request.getDeviceTxId())
                .terminalId(request.getTerminalId())
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

        // A Spring application event, not a direct RabbitMQ send: recordCollection runs inside
        // this class's @Transactional boundary, and rabbitTemplate.convertAndSend doesn't wait
        // for that transaction to commit — publishing straight to the broker here let the
        // consumer occasionally race the commit under burst load (query the row before it was
        // actually visible). CollectionGeocodeEventRelay only forwards this to RabbitMQ
        // @TransactionalEventListener(AFTER_COMMIT), so the row is guaranteed visible first.
        applicationEventPublisher.publishEvent(new CollectionGeocodeEvent(collection.getId(), collection.getLat(), collection.getLon()));

        auditCollectionRecorded(collection);
        return toResponse(collection, false);
    }

    /**
     * A lightweight pointer into the unified audit timeline, not a duplicate of the collection
     * itself — the full record (amount, denominations, geotag, reconciliation state) stays solely
     * in {@link Collection}, exported per-agent/per-client from there. This row exists only so a
     * reviewer scanning /audit sees that a collection happened at all, alongside logins/
     * suspensions/SOS, without needing to cross-reference a separate screen.
     */
    private void auditCollectionRecorded(Collection collection) {
        var agentInfo = agentDirectoryService.findAuditInfo(collection.getAgentId());
        String clientName = clientDirectoryService.findReceiptInfo(collection.getClientId()).fullName();
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.FINANCIAL)
                .eventType("COLLECTION_RECORDED")
                .actorType(AuditActorType.AGENT)
                .actorId(collection.getAgentId())
                .actorLabel(agentInfo.username())
                .branchId(agentInfo.branchId())
                .agentId(collection.getAgentId())
                .detailsKey("COLLECTION_RECORDED_DETAIL")
                .detailsParam1(String.valueOf(collection.getAmountXaf()))
                .detailsParam2(clientName)
                .build());
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
     * BR-03: an agent's cumulative cash-in-hand right now — regular collections plus any
     * agent-collected payments tagged elsewhere (e.g. activation fees, see
     * {@code savings.ActivationPayment}) — must never exceed their effective escrow ceiling.
     * Public so other modules whose agents physically receive cash (not just {@code Collection}
     * rows) can enforce the same lockout before accepting it.
     * <p>
     * "Cash-in-hand" means not yet reconciled, not "collected today" — once OfjService#reconcile
     * sweeps an agent's collections (cash physically handed over and counted), that cash no longer
     * counts against their ceiling, even if it's still the same calendar day. Using a calendar-day
     * window here would otherwise keep counting cash the agent doesn't have anymore, permanently
     * shrinking their usable ceiling for the rest of the day after every reconciliation.
     */
    public void enforceEscrowCeiling(UUID agentId, long amountXaf) {
        EscrowResponse escrow = escrowService.getStatus(agentId);
        Instant now = Instant.now();
        long cumulativeUnreconciled = collectionRepository.sumUnreconciledByAgent(agentId, now)
                + activationDirectoryService.sumUnreconciled(agentId, now);

        if (cumulativeUnreconciled + amountXaf > escrow.getEffectiveCeilingXaf()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Would exceed escrow ceiling (BR-03): cumulative " + cumulativeUnreconciled
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
                    "You are outside your assigned collection zone. Move back inside your geofence to collect here");
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
                    "You have a pending client activation payment awaiting confirmation. Resolve it before collecting more cash");
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
                        .reconciledAt(collection.getReconciledAt())
                        .syncStatus(collection.getSyncStatus())
                        .deviceTxId(collection.getDeviceTxId())
                        .terminalId(collection.getTerminalId())
                        .build())
                .toList();
    }

    /**
     * Back-Office agent oversight (branch manager/cashier/admin) — every collection this agent
     * recorded on one specific calendar day, client names resolved, newest first. Distinct from
     * {@link #findRecentByAgent}'s unbounded "last 50 ever": this lets a reviewer see exactly what
     * an agent collected and from whom on a given day, independent of reconciliation status.
     */
    public List<CollectionResponse> findByAgentAndDay(UUID agentId, LocalDate date) {
        Instant startOfDayUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDayUtc = startOfDayUtc.plus(1, ChronoUnit.DAYS);
        return findByAgentAndRange(agentId, startOfDayUtc, endOfDayUtc);
    }

    /**
     * Back-Office agent oversight, parametrized by an arbitrary period instead of one calendar
     * day — backs the Audit export's date-range picker (BR: every export honors the period the
     * user chose, never a fixed "today" or "everything").
     */
    public List<CollectionResponse> findByAgentAndRange(UUID agentId, Instant from, Instant to) {
        List<Collection> collections = collectionRepository.findByAgentIdInAndCollectedAtBetween(
                List.of(agentId), from, to);
        Map<UUID, String> namesByClientId = clientDirectoryService.findFullNames(
                collections.stream().map(Collection::getClientId).collect(Collectors.toSet()));

        return collections.stream()
                .sorted(Comparator.comparing(Collection::getCollectedAt).reversed())
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
                        .reconciledAt(collection.getReconciledAt())
                        .syncStatus(collection.getSyncStatus())
                        .deviceTxId(collection.getDeviceTxId())
                        .terminalId(collection.getTerminalId())
                        .build())
                .toList();
    }

    /**
     * Back-Office client transactions export (Financial & Transactional category) — every
     * collection recorded against this client within an arbitrary [from, to) window, newest
     * first. Agent identity is resolved by the caller from its own already-fetched agent list
     * (same pattern the OFJ oversight screens use), so this doesn't duplicate agent-name
     * resolution the way {@link #findByAgentAndRange} resolves client names.
     */
    public List<CollectionResponse> findByClientAndRange(UUID clientId, Instant from, Instant to) {
        return collectionRepository.findByClientIdAndCollectedAtBetween(clientId, from, to).stream()
                .sorted(Comparator.comparing(Collection::getCollectedAt).reversed())
                .map(collection -> CollectionResponse.builder()
                        .id(collection.getId())
                        .agentId(collection.getAgentId())
                        .clientId(collection.getClientId())
                        .amountXaf(collection.getAmountXaf())
                        .lat(collection.getLat())
                        .lon(collection.getLon())
                        .accuracyM(collection.getAccuracyM())
                        .locationName(collection.getLocationName())
                        .collectedAt(collection.getCollectedAt())
                        .reconciledAt(collection.getReconciledAt())
                        .syncStatus(collection.getSyncStatus())
                        .deviceTxId(collection.getDeviceTxId())
                        .terminalId(collection.getTerminalId())
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
                .reconciledAt(collection.getReconciledAt())
                .syncStatus(collection.getSyncStatus())
                .deviceTxId(collection.getDeviceTxId())
                .terminalId(collection.getTerminalId())
                .denominationLines(lines)
                .duplicate(duplicate)
                .build();
    }
}
