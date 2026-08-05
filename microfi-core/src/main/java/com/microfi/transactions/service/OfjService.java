package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.shared.dto.DenominationLineDto;
import com.microfi.shared.dto.ExportBatchResponse;
import com.microfi.shared.dto.ExportRequest;
import com.microfi.shared.dto.MiddlewareExportAck;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.ReconcileRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.VarianceRequest;
import com.microfi.transactions.domain.ExportBatch;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.domain.OfjPhysicalDenom;
import com.microfi.transactions.domain.OfjSession;
import com.microfi.transactions.domain.OfjSessionStatus;
import com.microfi.transactions.domain.VarianceDebt;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.ExportBatchRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
import com.microfi.transactions.repository.OfjPhysicalDenomRepository;
import com.microfi.transactions.repository.OfjSessionRepository;
import com.microfi.transactions.repository.VarianceDebtRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UC-16/17/18 — End-of-Day (OFJ): reconcile each agent's physical cash against the digital ledger
 * (BR-01: delta = physical - digital), formalise shortages as agent debt (BR-Var-01: shortages
 * only), and submit the day's closed session to the CBS via {@link CbsClientService} once every
 * line is resolved (BR-Export-01: closed sessions only).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OfjService {

    private final OfjSessionRepository ofjSessionRepository;
    private final OfjAgentLineRepository ofjAgentLineRepository;
    private final OfjPhysicalDenomRepository ofjPhysicalDenomRepository;
    private final VarianceDebtRepository varianceDebtRepository;
    private final ExportBatchRepository exportBatchRepository;
    private final CollectionRepository collectionRepository;
    private final CbsClientService cbsClientService;
    private final AgentDirectoryService agentDirectoryService;
    private final ActivationDirectoryService activationDirectoryService;

    public OfjSummaryResponse getSummary(UUID branchId) {
        return toSummary(getOrCreateSession(branchId));
    }

    public OfjAgentLineResponse reconcile(UUID branchId, ReconcileRequest request) {
        OfjSession session = getOrCreateSession(branchId);
        if (session.getStatus() != OfjSessionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OFJ session already closed for this branch/day");
        }

        long collectionsTotal = sumCollectionsForAgentToday(request.getAgentId());
        long activationsTotal = sumActivationsForAgentToday(request.getAgentId());
        long digitalTotal = collectionsTotal + activationsTotal;
        long physicalTotal = request.getPhysicalDenominationLines().stream()
                .mapToLong(line -> line.getFaceValueXaf() * line.getQuantity())
                .sum();
        long delta = physicalTotal - digitalTotal;

        OfjAgentLine line = ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), request.getAgentId())
                .orElseGet(() -> OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(request.getAgentId()).build());
        line.setDigitalTotalXaf(digitalTotal);
        line.setCollectionsTotalXaf(collectionsTotal);
        line.setActivationsTotalXaf(activationsTotal);
        line.setPhysicalTotalXaf(physicalTotal);
        line.setDeltaXaf(delta);
        ofjAgentLineRepository.save(line);

        ofjPhysicalDenomRepository.deleteByOfjAgentLineId(line.getId());
        for (DenominationLineDto denom : request.getPhysicalDenominationLines()) {
            ofjPhysicalDenomRepository.save(OfjPhysicalDenom.builder()
                    .id(UUID.randomUUID())
                    .ofjAgentLineId(line.getId())
                    .faceValueXaf(denom.getFaceValueXaf())
                    .quantity(denom.getQuantity())
                    .build());
        }

        maybeCloseSession(session);
        return toLineResponse(line);
    }

    public VarianceDebtResponse recordVariance(VarianceRequest request) {
        OfjAgentLine line = ofjAgentLineRepository.findById(request.getOfjAgentLineId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OFJ agent line not found: " + request.getOfjAgentLineId()));

        if (line.getDeltaXaf() >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only negative variances (shortages) can be recorded as agent debt (BR-Var-01)");
        }
        if (varianceDebtRepository.findByOfjAgentLineId(line.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Variance debt already recorded for this line");
        }

        VarianceDebt debt = VarianceDebt.builder()
                .id(UUID.randomUUID())
                .agentId(line.getAgentId())
                .ofjAgentLineId(line.getId())
                .amountXaf(Math.abs(line.getDeltaXaf()))
                .build();
        varianceDebtRepository.save(debt);

        ofjSessionRepository.findById(line.getOfjId()).ifPresent(this::maybeCloseSession);
        return toDebtResponse(debt);
    }

    public ExportBatchResponse exportDaily(UUID branchId, ExportRequest request) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OfjSession session = ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)
                .filter(s -> s.getStatus() == OfjSessionStatus.CLOSED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No closed OFJ session for branch " + branchId + " on " + today + " (BR-Export-01)"));

        String format = (request.getFormat() == null || request.getFormat().isBlank()) ? "CSV" : request.getFormat();
        String fileUri = "export/" + branchId + "/" + session.getBusinessDate() + "." + format.toLowerCase();

        // Blocking on the middleware call is safe here: this method always runs on the
        // boundedElastic worker the controller dispatches it to, never on the Netty event loop.
        MiddlewareExportAck ack = cbsClientService.submitDailyExport(branchId, fileUri, format).block();

        ExportBatch batch = ExportBatch.builder()
                .id(UUID.randomUUID())
                .ofjId(session.getId())
                .fileUri(fileUri)
                .format(format)
                .ackStatus(ack != null && ack.isAcknowledged() ? "ACKNOWLEDGED:" + ack.getAckReference() : "FAILED")
                .build();
        exportBatchRepository.save(batch);

        return toExportResponse(batch);
    }

    private OfjSession getOrCreateSession(UUID branchId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)
                .orElseGet(() -> ofjSessionRepository.save(OfjSession.builder()
                        .id(UUID.randomUUID())
                        .branchId(branchId)
                        .businessDate(today)
                        .build()));
    }

    /**
     * Closes the session only once every active agent in the branch — not merely every agent
     * who has reconciled so far — has a resolved line. Comparing against
     * {@link AgentDirectoryService#findActiveAgentIdsByBranch} (rather than just the lines that
     * already exist) is what stops the session from closing the moment the *first* agent to
     * reconcile happens to balance exactly, while the rest of the branch hasn't reported in yet.
     */
    private void maybeCloseSession(OfjSession session) {
        List<UUID> activeAgentIds = agentDirectoryService.findActiveAgentIdsByBranch(session.getBranchId());
        if (activeAgentIds.isEmpty()) {
            return;
        }

        Map<UUID, OfjAgentLine> lineByAgent = ofjAgentLineRepository.findByOfjId(session.getId()).stream()
                .collect(Collectors.toMap(OfjAgentLine::getAgentId, line -> line));

        boolean allAgentsResolved = activeAgentIds.stream()
                .allMatch(agentId -> {
                    OfjAgentLine line = lineByAgent.get(agentId);
                    return line != null && isResolved(line);
                });

        if (allAgentsResolved) {
            session.setStatus(OfjSessionStatus.CLOSED);
            session.setClosedAt(Instant.now());
            ofjSessionRepository.save(session);
        }
    }

    private boolean isResolved(OfjAgentLine line) {
        return line.getDeltaXaf() >= 0 || varianceDebtRepository.findByOfjAgentLineId(line.getId()).isPresent();
    }

    private long sumCollectionsForAgentToday(UUID agentId) {
        Instant startOfDayUtc = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDayUtc = startOfDayUtc.plus(1, ChronoUnit.DAYS);
        return collectionRepository.sumAmountByAgentAndWindow(agentId, startOfDayUtc, endOfDayUtc);
    }

    /** FR-19 activation fees collected in cash also count as digital totals the agent must reconcile against. */
    private long sumActivationsForAgentToday(UUID agentId) {
        Instant startOfDayUtc = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDayUtc = startOfDayUtc.plus(1, ChronoUnit.DAYS);
        return activationDirectoryService.sumAmountByAgentAndWindow(agentId, startOfDayUtc, endOfDayUtc);
    }

    private OfjSummaryResponse toSummary(OfjSession session) {
        List<OfjAgentLineResponse> lines = ofjAgentLineRepository.findByOfjId(session.getId()).stream()
                .map(this::toLineResponse)
                .toList();
        return OfjSummaryResponse.builder()
                .sessionId(session.getId())
                .branchId(session.getBranchId())
                .businessDate(session.getBusinessDate())
                .status(session.getStatus().name())
                .agentLines(lines)
                .build();
    }

    private OfjAgentLineResponse toLineResponse(OfjAgentLine line) {
        return OfjAgentLineResponse.builder()
                .id(line.getId())
                .agentId(line.getAgentId())
                .digitalTotalXaf(line.getDigitalTotalXaf())
                // Boxed on the entity so the column could migrate onto existing rows (see
                // OfjAgentLine) — null only for pre-migration rows never reconciled again.
                .collectionsTotalXaf(line.getCollectionsTotalXaf() == null ? 0 : line.getCollectionsTotalXaf())
                .activationsTotalXaf(line.getActivationsTotalXaf() == null ? 0 : line.getActivationsTotalXaf())
                .physicalTotalXaf(line.getPhysicalTotalXaf())
                .deltaXaf(line.getDeltaXaf())
                .resolved(isResolved(line))
                .build();
    }

    private VarianceDebtResponse toDebtResponse(VarianceDebt debt) {
        return VarianceDebtResponse.builder()
                .id(debt.getId())
                .agentId(debt.getAgentId())
                .ofjAgentLineId(debt.getOfjAgentLineId())
                .amountXaf(debt.getAmountXaf())
                .status(debt.getStatus().name())
                .createdAt(debt.getCreatedAt())
                .build();
    }

    private ExportBatchResponse toExportResponse(ExportBatch batch) {
        return ExportBatchResponse.builder()
                .id(batch.getId())
                .ofjId(batch.getOfjId())
                .fileUri(batch.getFileUri())
                .format(batch.getFormat())
                .generatedAt(batch.getGeneratedAt())
                .ackStatus(batch.getAckStatus())
                .build();
    }
}
