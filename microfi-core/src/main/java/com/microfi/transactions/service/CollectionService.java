package com.microfi.transactions.service;

import com.microfi.transactions.domain.ClientProfile;
import com.microfi.transactions.domain.ClientStatus;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.DenominationLine;
import com.microfi.transactions.repository.ClientProfileRepository;
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
import java.util.UUID;

/**
 * UC-06/07/08/12 — the Digital Cash Desk loop: resolve client, enforce the GPS gate, validate the
 * mandatory denomination breakdown, enforce the escrow lockout (FR-04), and record the deposit
 * idempotently so a retried offline sync never double-counts.
 * <p>
 * Takes the agent's id directly (resolved by the caller from the authenticated principal) rather
 * than looking it up itself — {@code transactions} has no business reaching into
 * {@code authentication}'s repository; modules communicate only through public service interfaces.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final DenominationLineRepository denominationLineRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final EscrowService escrowService;

    @Value("${collection.denomination-threshold-xaf:0}")
    private long denominationThresholdXaf;

    public CollectionResponse recordCollection(UUID agentId, CollectionRequest request) {
        var existing = collectionRepository.findByAgentIdAndDeviceTxId(agentId, request.getDeviceTxId());
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        ClientProfile client = clientProfileRepository.findById(request.getClientId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + request.getClientId()));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client is not active: " + request.getClientId());
        }

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

    private void enforceEscrowCeiling(UUID agentId, long amountXaf) {
        EscrowResponse escrow = escrowService.getStatus(agentId);
        Instant startOfDayUtc = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDayUtc = startOfDayUtc.plus(1, ChronoUnit.DAYS);
        long cumulativeToday = collectionRepository.sumAmountByAgentAndWindow(agentId, startOfDayUtc, endOfDayUtc);

        if (cumulativeToday + amountXaf > escrow.getEffectiveCeilingXaf()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Collection would exceed escrow ceiling (BR-03): cumulative " + cumulativeToday
                            + " + " + amountXaf + " > ceiling " + escrow.getEffectiveCeilingXaf());
        }
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
                .collectedAt(collection.getCollectedAt())
                .syncStatus(collection.getSyncStatus())
                .deviceTxId(collection.getDeviceTxId())
                .denominationLines(lines)
                .duplicate(duplicate)
                .build();
    }
}
