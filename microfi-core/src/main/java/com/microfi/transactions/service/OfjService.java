package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.DenominationLineDto;
import com.microfi.shared.dto.ExportBatchResponse;
import com.microfi.shared.dto.ExportRequest;
import com.microfi.shared.dto.MiddlewareCollectionLine;
import com.microfi.shared.dto.MiddlewareExportAck;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjPendingLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.ReconcileRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.VarianceRequest;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.ExportBatch;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.domain.OfjPhysicalDenom;
import com.microfi.transactions.domain.OfjSession;
import com.microfi.transactions.domain.OfjSessionStatus;
import com.microfi.transactions.domain.VarianceDebt;
import com.microfi.transactions.domain.VarianceDebtStatus;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.ExportBatchRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
import com.microfi.transactions.repository.OfjPhysicalDenomRepository;
import com.microfi.transactions.repository.OfjSessionRepository;
import com.microfi.transactions.repository.VarianceDebtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
@Slf4j
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
    private final ClientDirectoryService clientDirectoryService;

    public OfjSummaryResponse getSummary(UUID branchId) {
        return toSummary(getOrCreateSession(branchId));
    }

    /**
     * {@code date == null} or today: same live behaviour as {@link #getSummary(UUID)} (auto-creates
     * today's session if none exists yet). Any other date is read-only history — a past day either
     * has a session or it doesn't; there's nothing to auto-create for a day that's already over.
     */
    public OfjSummaryResponse getSummary(UUID branchId, LocalDate date) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (date == null || date.equals(today)) {
            return toSummary(getOrCreateSession(branchId));
        }
        OfjSession session = ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, date)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No OFJ session for branch " + branchId + " on " + date));
        return toSummary(session);
    }

    /**
     * Active agents in the branch who currently have unreconciled digital cash the cashier still
     * needs to look at. Driven directly by {@code reconciledAt IS NULL} (see
     * CollectionRepository#sumUnreconciledByAgent), not by comparing against a stale
     * {@code OfjAgentLine} snapshot — an agent's unreconciled total already reflects everything
     * since their last reconciliation, including a multi-day-offline backlog that only just synced.
     */
    public List<OfjPendingLineResponse> listPendingAgents(UUID branchId) {
        List<UUID> activeAgentIds = agentDirectoryService.findActiveAgentIdsByBranch(branchId);
        if (activeAgentIds.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<OfjPendingLineResponse> pending = new ArrayList<>();
        for (UUID agentId : activeAgentIds) {
            long collectionsTotal = collectionRepository.sumUnreconciledByAgent(agentId, now);
            long activationsTotal = activationDirectoryService.sumUnreconciled(agentId, now);
            long digitalTotal = collectionsTotal + activationsTotal;
            if (digitalTotal <= 0) {
                continue;
            }
            pending.add(OfjPendingLineResponse.builder()
                    .agentId(agentId)
                    .collectionsTotalXaf(collectionsTotal)
                    .activationsTotalXaf(activationsTotal)
                    .digitalTotalXaf(digitalTotal)
                    .build());
        }
        return pending;
    }

    /** Back-Office reports/history screen: every past session for the branch, most recent first. */
    public List<OfjSummaryResponse> listHistory(UUID branchId) {
        return ofjSessionRepository.findByBranchIdOrderByBusinessDateDesc(branchId).stream()
                .map(this::toSummary)
                .toList();
    }

    /** For an admin viewing a single agent's outstanding shortages (e.g. from an agent detail screen). */
    public List<VarianceDebtResponse> listVarianceDebtsForAgent(UUID agentId, boolean openOnly) {
        List<VarianceDebt> debts = openOnly
                ? varianceDebtRepository.findByAgentIdAndStatusOrderByCreatedAtDesc(agentId, VarianceDebtStatus.OPEN)
                : varianceDebtRepository.findByAgentIdOrderByCreatedAtDesc(agentId);
        return debts.stream().map(this::toDebtResponse).toList();
    }

    /** For a branch-wide "who owes what" dashboard. */
    public List<VarianceDebtResponse> listVarianceDebtsForBranch(UUID branchId, boolean openOnly) {
        List<UUID> agentIds = agentDirectoryService.findAgentIdsByBranch(branchId);
        if (agentIds.isEmpty()) {
            return List.of();
        }
        List<VarianceDebt> debts = openOnly
                ? varianceDebtRepository.findByAgentIdInAndStatusOrderByCreatedAtDesc(agentIds, VarianceDebtStatus.OPEN)
                : varianceDebtRepository.findByAgentIdInOrderByCreatedAtDesc(agentIds);
        return debts.stream().map(this::toDebtResponse).toList();
    }

    public OfjAgentLineResponse reconcile(UUID branchId, ReconcileRequest request) {
        if (!agentDirectoryService.isBranchPastCloseTime(branchId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot reconcile before the branch's closing time — the day's cash isn't all collected yet");
        }
        OfjSession session = getOrCreateSession(branchId);
        if (session.getStatus() == OfjSessionStatus.CLOSED) {
            // "Closed" only ever meant "every agent known at the time balanced" (maybeCloseSession)
            // — nothing stops an agent from collecting more afterward. Reopening is safe as long as
            // nothing has been exported to the CBS yet; once it has, that day's numbers are final
            // and new cash belongs to whatever session covers it going forward.
            if (exportBatchRepository.findByOfjId(session.getId()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "OFJ session already exported for this branch/day — cash recorded afterward can't be added to it");
            }
            session.setStatus(OfjSessionStatus.OPEN);
            session.setClosedAt(null);
            ofjSessionRepository.save(session);
        } else if (session.getStatus() != OfjSessionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OFJ session is not open for this branch/day");
        }

        // A fixed instant, not "current DB state" — sumUnreconciledByAgent and markReconciled below
        // both filter on collectedAt/paidAt < cutoff, so they see exactly the same rows regardless
        // of anything concurrently syncing in mid-transaction. Whatever's still unreconciled and
        // older than this moment gets swept up now — including a multi-day-offline agent's entire
        // backlog, however far back it goes, not just "today".
        Instant cutoff = Instant.now();
        long newCollections = collectionRepository.sumUnreconciledByAgent(request.getAgentId(), cutoff);
        long newActivations = activationDirectoryService.sumUnreconciled(request.getAgentId(), cutoff);
        long physicalTotal = request.getPhysicalDenominationLines().stream()
                .mapToLong(line -> line.getFaceValueXaf() * line.getQuantity())
                .sum();

        OfjAgentLine line = ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), request.getAgentId())
                .orElseGet(() -> OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(request.getAgentId())
                        .digitalTotalXaf(0L).collectionsTotalXaf(0L).activationsTotalXaf(0L).build());
        // Accumulate, don't overwrite: nothing stops an agent reconciling twice in one session (they
        // sync more after balancing once), and each newly-unreconciled sweep is additional cash on
        // top of whatever this line already counted, not a replacement for it. physicalTotalXaf is
        // the opposite — it's what the cashier just counted in hand right now, so it does overwrite.
        long digitalTotal = nz(line.getCollectionsTotalXaf()) + nz(line.getActivationsTotalXaf()) + newCollections + newActivations;
        line.setCollectionsTotalXaf(nz(line.getCollectionsTotalXaf()) + newCollections);
        line.setActivationsTotalXaf(nz(line.getActivationsTotalXaf()) + newActivations);
        line.setDigitalTotalXaf(digitalTotal);
        line.setPhysicalTotalXaf(physicalTotal);
        line.setDeltaXaf(physicalTotal - digitalTotal);
        ofjAgentLineRepository.save(line);

        collectionRepository.markReconciled(request.getAgentId(), cutoff, line.getId());
        activationDirectoryService.markReconciled(request.getAgentId(), cutoff, line.getId());

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
        if (exportBatchRepository.findByOfjId(session.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This session was already exported — see the automatic export triggered when it closed");
        }

        String format = (request.getFormat() == null || request.getFormat().isBlank()) ? "CSV" : request.getFormat();
        // Blocking on the middleware call is safe here: this method always runs on the
        // boundedElastic worker the controller dispatches it to, never on the Netty event loop.
        ExportBatch batch = doExport(branchId, session, format);
        return toExportResponse(batch);
    }

    /**
     * The actual CBS posting + export-file bookkeeping, shared by the manual {@link #exportDaily}
     * endpoint and {@link #maybeCloseSession}'s automatic trigger — a client's balance/history is
     * entirely CBS-driven (savings.ClientSelfService), so without this, "reconciled" cash would
     * sit invisible to the client until someone remembered to click Export separately.
     */
    private ExportBatch doExport(UUID branchId, OfjSession session, String format) {
        String fileUri = "export/" + branchId + "/" + session.getBusinessDate() + "." + format.toLowerCase();
        postCollectionsToLedger(branchId, session);
        MiddlewareExportAck ack = cbsClientService.submitDailyExport(branchId, fileUri, format).block();

        ExportBatch batch = ExportBatch.builder()
                .id(UUID.randomUUID())
                .ofjId(session.getId())
                .fileUri(fileUri)
                .format(format)
                .ackStatus(ack != null && ack.isAcknowledged() ? "ACKNOWLEDGED:" + ack.getAckReference() : "FAILED")
                .build();
        exportBatchRepository.save(batch);
        return batch;
    }

    /**
     * Posts the branch's closed-session cash — both regular {@link Collection} deposits and
     * finalized UC-19 activation fees ({@code ActivationPayment}, real cash the same as a
     * Collection, see {@link ActivationDirectoryService}) — to the CBS ledger, so the mock (and
     * any real adapter) genuinely reflects what clients contributed/paid rather than
     * {@code getBalance}/{@code getHistory} being disconnected from real activity. Idempotency-keyed
     * on the session id so a retried export never double-posts the same day's cash.
     */
    private void postCollectionsToLedger(UUID branchId, OfjSession session) {
        List<UUID> activeAgentIds = agentDirectoryService.findActiveAgentIdsByBranch(branchId);
        if (activeAgentIds.isEmpty()) {
            return;
        }

        // Posts exactly what THIS session's lines actually reconciled, not "whatever has a
        // collectedAt matching this calendar date" — a collection recorded offline days ago and
        // only just synced keeps its original collectedAt, so a date-window query here would
        // silently never post it even though reconcile() already counted it in digitalTotalXaf.
        List<UUID> lineIds = ofjAgentLineRepository.findByOfjId(session.getId()).stream().map(OfjAgentLine::getId).toList();

        List<MiddlewareCollectionLine> lines = new ArrayList<>();
        collectionRepository.findByReconciledInLineIdIn(lineIds).stream()
                .map(collection -> MiddlewareCollectionLine.builder()
                        .collectionId(collection.getId())
                        .memberId(clientDirectoryService.findCbsRef(collection.getClientId()))
                        .amountXaf(collection.getAmountXaf())
                        .collectedAt(collection.getCollectedAt())
                        .build())
                .forEach(lines::add);
        activationDirectoryService.findByReconciledInLineIds(lineIds).stream()
                .map(payment -> MiddlewareCollectionLine.builder()
                        .collectionId(payment.id())
                        .memberId(clientDirectoryService.findCbsRef(payment.clientId()))
                        .amountXaf(payment.amountXaf())
                        .collectedAt(payment.paidAt())
                        .build())
                .forEach(lines::add);

        if (lines.isEmpty()) {
            return;
        }

        cbsClientService.postTransactions(lines, "ofj-export-" + session.getId())
                .doOnError(e -> log.error("Failed to post branch {} cash to CBS ledger for session {}: {}",
                        branchId, session.getId(), e.getMessage()))
                .onErrorComplete()
                .block();
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

        // Design handoff §6.1: close stays blocked while any agent's app still has collections
        // queued locally, regardless of how the reconciled lines look — that cash hasn't reached
        // the server yet, so it isn't in digital_total_xaf, so a delta==0 here would be wrong.
        if (agentDirectoryService.hasPendingUnsyncedCollections(activeAgentIds)) {
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
            autoExportOnClose(session);
        }
    }

    /**
     * The whole point of closing the session as soon as every agent balances: the day's cash
     * should reach the CBS — and so the client's own balance/history — immediately, not whenever
     * someone remembers to click a separate Export button. A CBS/middleware hiccup here must
     * never undo the reconciliation that already succeeded; it's logged, and the manual
     * {@link #exportDaily} endpoint stays available to retry once the CBS is reachable again.
     */
    private void autoExportOnClose(OfjSession session) {
        if (exportBatchRepository.findByOfjId(session.getId()).isPresent()) {
            return;
        }
        try {
            doExport(session.getBranchId(), session, "CSV");
        } catch (Exception e) {
            log.error("Automatic CBS export failed for branch {} session {}: {} — retry via POST /ofj/{}/export",
                    session.getBranchId(), session.getId(), e.getMessage(), session.getBranchId());
        }
    }

    private boolean isResolved(OfjAgentLine line) {
        return line.getDeltaXaf() >= 0 || varianceDebtRepository.findByOfjAgentLineId(line.getId()).isPresent();
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
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
