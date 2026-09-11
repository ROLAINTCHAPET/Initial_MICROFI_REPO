package com.microfi.transactions.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.service.ActivationCashLine;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.AdminPendingConfirmationResponse;
import com.microfi.shared.dto.CollectionResponse;
import com.microfi.shared.dto.DenominationLineDto;
import com.microfi.shared.dto.EndDayResponse;
import com.microfi.shared.dto.ExportBatchResponse;
import com.microfi.shared.dto.ExportRequest;
import com.microfi.shared.dto.ExportableSummaryResponse;
import com.microfi.shared.dto.MiddlewareCollectionLine;
import com.microfi.shared.dto.MiddlewareExportAck;
import com.microfi.shared.dto.MiddlewareTransactionPostResult;
import com.microfi.events.OfjClosingExportCompletedEvent;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjClosingExportAlertResponse;
import com.microfi.shared.dto.OfjPendingLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.PendingReconciliationLineResponse;
import com.microfi.shared.dto.ReconcileRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.VarianceRequest;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.CollectionReconciliationStatus;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import com.microfi.transactions.domain.ExportBatch;
import com.microfi.transactions.domain.ExportTrigger;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.domain.OfjPhysicalDenom;
import com.microfi.transactions.domain.OfjSession;
import com.microfi.transactions.domain.OfjSessionStatus;
import com.microfi.transactions.domain.VarianceDebt;
import com.microfi.transactions.domain.VarianceDebtStatus;
import com.microfi.transactions.repository.CollectionRejectionRequestRepository;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.ExportBatchRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
import com.microfi.transactions.repository.OfjPhysicalDenomRepository;
import com.microfi.transactions.repository.OfjSessionRepository;
import com.microfi.transactions.repository.VarianceDebtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Optional;
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
    private final CollectionRejectionRequestRepository collectionRejectionRequestRepository;
    private final CbsClientService cbsClientService;
    private final AgentDirectoryService agentDirectoryService;
    private final ActivationDirectoryService activationDirectoryService;
    private final ClientDirectoryService clientDirectoryService;
    private final AuditService auditService;
    private final ApplicationEventPublisher applicationEventPublisher;

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
     * Active agents in the branch who currently have digital cash the cashier still hasn't
     * physically counted yet. Driven directly by {@code CollectionReconciliationStatus.UNRECONCILED}
     * (see {@code CollectionRepository#sumUncountedByAgent}), not by comparing against a stale
     * {@code OfjAgentLine} snapshot — an agent's uncounted total already reflects everything since
     * their last reconciliation, including a multi-day-offline backlog that only just synced.
     * Deliberately excludes collections already swept into a line and merely awaiting the agent's
     * own confirmation — the cashier has nothing left to do for those.
     */
    public List<OfjPendingLineResponse> listPendingAgents(UUID branchId) {
        List<UUID> activeAgentIds = agentDirectoryService.findActiveAgentIdsByBranch(branchId);
        if (activeAgentIds.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<OfjPendingLineResponse> pending = new ArrayList<>();
        for (UUID agentId : activeAgentIds) {
            long collectionsTotal = collectionRepository.sumUncountedByAgent(agentId, now);
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

    /** Same history screen, restricted to a chosen [from, to] business-date range — backs the Audit export's date-range picker. */
    public List<OfjSummaryResponse> listHistory(UUID branchId, LocalDate from, LocalDate to) {
        return ofjSessionRepository.findByBranchIdAndBusinessDateBetweenOrderByBusinessDateDesc(branchId, from, to).stream()
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
        OfjSession session = getOrCreateSession(branchId);
        if (session.getStatus() == OfjSessionStatus.CLOSED) {
            // "Closed" only ever meant "every agent known at the time balanced" (maybeCloseSession)
            // — nothing stops an agent from collecting more afterward. Reopening is always safe now
            // that export is per-collection-idempotent rather than session-final (see
            // CollectionRepository's confirmed-and-unexported query): an already-exported
            // collection simply never gets selected again by a later export run, whether the
            // session was ever reopened in between or not.
            session.setStatus(OfjSessionStatus.OPEN);
            session.setClosedAt(null);
            ofjSessionRepository.save(session);
        } else if (session.getStatus() != OfjSessionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OFJ session is not open for this branch/day");
        }

        // A fixed instant, not "current DB state" — sumUncountedByAgent and markPendingConfirmation
        // below both filter on collectedAt/paidAt < cutoff, so they see exactly the same rows
        // regardless of anything concurrently syncing in mid-transaction. Whatever the cashier
        // hasn't looked at yet and is older than this moment gets swept up now — including a
        // multi-day-offline agent's entire backlog, however far back it goes, not just "today".
        Instant cutoff = Instant.now();
        long newCollections = collectionRepository.sumUncountedByAgent(request.getAgentId(), cutoff);
        long newActivations = activationDirectoryService.sumUnreconciled(request.getAgentId(), cutoff);
        // A rejection-triggered requeue (Agent#carriedPhysicalXaf) puts a collection back into
        // newCollections above so it can be re-confirmed, but that cash was already physically
        // handed over in an earlier sweep — folded in here so the cashier isn't expected to
        // re-enter cash they already have, which otherwise reads as a fresh shortage.
        long physicalTotal = request.getPhysicalDenominationLines().stream()
                .mapToLong(line -> line.getFaceValueXaf() * line.getQuantity())
                .sum() + agentDirectoryService.consumeCarriedPhysicalXaf(request.getAgentId());

        OfjAgentLine line = ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), request.getAgentId())
                .orElseGet(() -> OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(request.getAgentId())
                        .digitalTotalXaf(0L).collectionsTotalXaf(0L).activationsTotalXaf(0L).build());
        // Both digitalTotalXaf and physicalTotalXaf accumulate across repeated reconciliations in
        // the same session (nothing stops an agent syncing more cash after already balancing once)
        // — each is the day's running total for that agent, so the branch-wide OFJ summary's "Total
        // numérique"/"Total physique" (see ofj/page.tsx) only ever disagree by exactly the amount of
        // an open shortage, never by cash that was already handed over and counted in an earlier,
        // balanced sweep. deltaXaf, though, compares like with like for THIS sweep alone — this
        // sweep's physical count against this sweep's own digital total (newCollections +
        // newActivations) — so a repeat reconciliation isn't flagged as short by everything already
        // settled earlier in the session even when each individual hand-over balanced perfectly.
        long newDigitalTotal = newCollections + newActivations;
        long digitalTotal = nz(line.getCollectionsTotalXaf()) + nz(line.getActivationsTotalXaf()) + newDigitalTotal;
        line.setCollectionsTotalXaf(nz(line.getCollectionsTotalXaf()) + newCollections);
        line.setActivationsTotalXaf(nz(line.getActivationsTotalXaf()) + newActivations);
        line.setDigitalTotalXaf(digitalTotal);
        line.setPhysicalTotalXaf(nz(line.getPhysicalTotalXaf()) + physicalTotal);
        line.setDeltaXaf(physicalTotal - newDigitalTotal);
        line.setLastCountedAt(cutoff);
        ofjAgentLineRepository.save(line);

        collectionRepository.markPendingConfirmation(request.getAgentId(), cutoff, line.getId());
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

    /**
     * This agent's own reconciliation lines still awaiting their confirmation — the mobile app's
     * "you need to confirm" screen. Per-line, not per-collection: a cashier's count can bundle
     * dozens of collections, and per-collection confirmation taps would be unusable (see
     * CollectionReconciliationStatus's doc). The total/count shown are scoped to exactly the
     * still-{@code PENDING_AGENT_CONFIRMATION} collections under each line, not the line's whole
     * accumulated history — a repeat same-day sweep reuses the same line id, so a line can mix an
     * already-confirmed earlier batch with a newer pending one.
     */
    public List<PendingReconciliationLineResponse> listPendingConfirmationLines(UUID agentId) {
        List<UUID> lineIds = collectionRepository.findDistinctPendingConfirmationLineIdsByAgent(agentId);
        if (lineIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, OfjAgentLine> linesById = ofjAgentLineRepository.findAllById(lineIds).stream()
                .collect(Collectors.toMap(OfjAgentLine::getId, l -> l));
        return lineIds.stream()
                .map(lineId -> PendingReconciliationLineResponse.builder()
                        .lineId(lineId)
                        .totalXaf(collectionRepository.sumByReconciledInLineIdAndReconciliationStatus(lineId, CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION))
                        .collectionCount(collectionRepository.countByReconciledInLineIdAndReconciliationStatusAndVoidedAtIsNull(lineId, CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION))
                        .lastCountedAt(linesById.get(lineId) != null ? linesById.get(lineId).getLastCountedAt() : null)
                        .build())
                .toList();
    }

    /**
     * Branch-wide equivalent of {@link #listPendingConfirmationLines} — the Back-Office "En
     * attente" view (a dedicated queue, distinct from the badge shown on an already-resolved
     * line, so a manager isn't left guessing what "N en attente de confirmation" actually refers
     * to). Same per-line, not per-collection scoping, for the same reason.
     */
    public List<AdminPendingConfirmationResponse> listPendingConfirmationsForBranch(UUID branchId) {
        List<UUID> agentIds = agentDirectoryService.findAgentIdsByBranch(branchId);
        if (agentIds.isEmpty()) {
            return List.of();
        }
        List<UUID> lineIds = collectionRepository.findDistinctPendingConfirmationLineIdsByAgentIn(agentIds);
        if (lineIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, OfjAgentLine> linesById = ofjAgentLineRepository.findAllById(lineIds).stream()
                .collect(Collectors.toMap(OfjAgentLine::getId, l -> l));
        return lineIds.stream()
                .map(lineId -> {
                    OfjAgentLine line = linesById.get(lineId);
                    return AdminPendingConfirmationResponse.builder()
                            .lineId(lineId)
                            .agentId(line != null ? line.getAgentId() : null)
                            .totalXaf(collectionRepository.sumByReconciledInLineIdAndReconciliationStatus(lineId, CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION))
                            .collectionCount(collectionRepository.countByReconciledInLineIdAndReconciliationStatusAndVoidedAtIsNull(lineId, CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION))
                            .lastCountedAt(line != null ? line.getLastCountedAt() : null)
                            .build();
                })
                .toList();
    }

    /**
     * The individual collections currently awaiting this agent's confirmation on one line — lets
     * them review exactly what's in the batch before confirming, or pick one to request rejection
     * on, rather than only ever seeing the line's aggregate total. Scoped to {@code
     * PENDING_AGENT_CONFIRMATION} specifically, not merely "not voided": a repeat same-day cashier
     * sweep reuses the same {@code OfjAgentLine} row (see {@code #reconcile}'s find-or-create), so
     * a line can mix an already-{@code CONFIRMED} earlier batch with a newer pending one under the
     * same id. Without this filter, a collection the agent already confirmed (or whose rejection
     * was already approved) would keep showing up here looking exactly as reviewable/rejectable as
     * something genuinely still awaiting them — confusing at best, and lets a stale "Request
     * Rejection" tap surface a 409 the agent has no way to anticipate.
     */
    public List<CollectionResponse> listCollectionsForLine(UUID agentId, UUID lineId) {
        OfjAgentLine line = ofjAgentLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation line not found: " + lineId));
        if (!line.getAgentId().equals(agentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view another agent's reconciliation");
        }
        List<Collection> collections = collectionRepository.findByReconciledInLineId(lineId).stream()
                .filter(c -> c.getVoidedAt() == null && c.getReconciliationStatus() == CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION)
                .toList();
        return collections.stream()
                .map(collection -> CollectionResponse.builder()
                        .id(collection.getId())
                        .agentId(collection.getAgentId())
                        .clientId(collection.getClientId())
                        .clientName(clientDirectoryService.findReceiptInfo(collection.getClientId()).fullName())
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
     * The agent's own sign-off on a cashier's physical count — the only thing that actually frees
     * their escrow ceiling (see CollectionRepository#sumUnreconciledByAgent). Requires the agent's
     * own transaction PIN, the same check a collection itself requires
     * (AgentDirectoryService#verifyTransactionPin) — confirming this attests it was genuinely them,
     * not just whoever is holding an unlocked phone. Verified before the line is even looked up,
     * same precedence CollectionService#recordCollection gives its own PIN check. Also verifies the
     * line genuinely belongs to this agent before touching it, same "never trust the caller's
     * claimed ownership" principle as CollectionController resolving the agent from the JWT, not a
     * request field.
     */
    public void confirmReconciliation(UUID agentId, UUID lineId, String pin) {
        agentDirectoryService.verifyTransactionPin(agentId, pin);
        OfjAgentLine line = ofjAgentLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reconciliation line not found: " + lineId));
        if (!line.getAgentId().equals(agentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot confirm another agent's reconciliation");
        }
        int updated = collectionRepository.markAgentConfirmed(lineId, Instant.now(), com.microfi.transactions.domain.CollectionConfirmedBy.AGENT);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nothing awaiting confirmation on this line");
        }
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

    /**
     * ADMIN-only write-off: clears an agent's shortage without the underlying record ever being
     * edited or deleted (BR-Var-02) — {@code amountXaf}/{@code agentId}/{@code createdAt} on the
     * original row are untouched; this only transitions {@code status} and records who cleared it,
     * why, and the supporting proof document (VarianceDebtController#writeOff resolves and stores
     * that file before calling in here, same split as EscrowController#topUp).
     */
    public VarianceDebtResponse writeOffVarianceDebt(UUID debtId, String reason, String proofPath, UUID writtenOffBy) {
        VarianceDebt debt = varianceDebtRepository.findById(debtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variance debt not found: " + debtId));
        if (debt.getStatus() != VarianceDebtStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an OPEN variance debt can be written off (currently " + debt.getStatus() + ")");
        }
        debt.setStatus(VarianceDebtStatus.WRITTEN_OFF);
        debt.setWrittenOffReason(reason);
        debt.setWrittenOffProofPath(proofPath);
        debt.setWrittenOffBy(writtenOffBy);
        debt.setWrittenOffAt(Instant.now());
        varianceDebtRepository.save(debt);
        return toDebtResponse(debt);
    }

    /** The stored write-off proof's relative disk path — 404s if the debt was never written off. */
    public String requireWriteOffProofPath(UUID debtId) {
        VarianceDebt debt = varianceDebtRepository.findById(debtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Variance debt not found: " + debtId));
        if (debt.getWrittenOffProofPath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This variance debt has no write-off proof on file");
        }
        return debt.getWrittenOffProofPath();
    }

    public ExportBatchResponse exportDaily(UUID branchId, ExportRequest request) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OfjSession session = ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)
                .filter(s -> s.getStatus() == OfjSessionStatus.CLOSED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No closed OFJ session for branch " + branchId + " on " + today + " (BR-Export-01)"));

        String format = (request.getFormat() == null || request.getFormat().isBlank()) ? "CSV" : request.getFormat();
        // Blocking on the middleware call is safe here: this method always runs on the
        // boundedElastic worker the controller dispatches it to, never on the Netty event loop.
        return doExport(branchId, session, format, ExportTrigger.MANUAL)
                .map(this::toExportResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Nothing new to export — every reconciled-and-confirmed collection for this session was already posted"));
    }

    /**
     * An agent pushing their own confirmed-but-unexported cash to the CBS immediately, without
     * waiting for the rest of the branch to balance or for the session to close — see
     * CollectionRepository#findByAgentIdAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull's
     * doc. Deliberately posts straight to the ledger with no {@link ExportBatch}/file record: a
     * single agent's slice of the day isn't "the day's export file" in the FR-18 sense, and these
     * collections are simply skipped (already {@code exportedAt}-stamped) by whichever real
     * session-level export run covers the rest of the branch later. Also marks the agent's day as
     * ended (see {@link AgentDirectoryService#markDayEnded}) — this is a one-way "I'm done for
     * today" signal, not just a cash push, so it also blocks any further collection until the next
     * business date.
     */
    public EndDayResponse exportForAgent(UUID agentId) {
        if (agentDirectoryService.hasEndedDayToday(agentId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You've already ended your day — any remaining cash is picked up by the branch's own close");
        }
        List<Collection> exportable = collectionRepository.findByAgentIdAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
                agentId, CollectionReconciliationStatus.CONFIRMED);
        if (exportable.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nothing confirmed and unexported to export yet");
        }
        // Content-derived, not just "agentId" — a fixed per-agent key would collide (and get
        // rejected by the middleware's idempotency check, see IdempotencyService#replay) the
        // moment a LATER End My Day call posts a genuinely different batch of collections than an
        // earlier one did. Deterministic on the same batch, so a true retry of the exact same
        // unexported set still replays safely instead of double-posting.
        String idempotencyKey = "ofj-end-my-day-" + agentId + "-" + UUID.nameUUIDFromBytes(
                exportable.stream().map(c -> c.getId().toString()).sorted().collect(Collectors.joining(",")).getBytes());
        int posted = postToLedger(exportable, List.of(), idempotencyKey);
        if (posted == 0) {
            // The CBS/middleware call itself failed (see #postToLedger's doOnError) — nothing was
            // actually posted, so the agent's day must NOT be marked ended and they should be able
            // to retry once the CBS is reachable again, exactly like OfjService#autoExportOnClose's
            // own "log and let the manual retry path pick it up" contract.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to reach the CBS right now — try again shortly");
        }
        long postedTotalXaf = exportable.stream().filter(c -> c.getExportedAt() != null).mapToLong(Collection::getAmountXaf).sum();
        // Having pushed their confirmed cash and signaled they're done, the agent can't record any
        // further collections today — see AgentDirectoryService#requireDayNotEnded.
        agentDirectoryService.markDayEnded(agentId, LocalDate.now(ZoneOffset.UTC));
        return EndDayResponse.builder().exportedCount(posted).exportedTotalXaf(postedTotalXaf).build();
    }

    /**
     * How much confirmed-but-unexported cash this agent has ready to push via
     * {@link #exportForAgent} — backs the mobile "End My Day" banner. Once the agent has already
     * ended today, this always reports zero regardless of any cash that becomes confirmed
     * afterward (e.g. a late reconciliation confirmation) — "End My Day" is a one-way signal and
     * must not resurface on the mobile app just because a little more cash trickled into
     * CONFIRMED status; that leftover is swept by the branch's own close instead.
     */
    public ExportableSummaryResponse getExportableSummary(UUID agentId) {
        if (agentDirectoryService.hasEndedDayToday(agentId)) {
            return ExportableSummaryResponse.builder().readyCount(0).readyTotalXaf(0).build();
        }
        return ExportableSummaryResponse.builder()
                .readyCount(collectionRepository.countByAgentIdAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
                        agentId, CollectionReconciliationStatus.CONFIRMED))
                .readyTotalXaf(collectionRepository.sumByAgentIdAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
                        agentId, CollectionReconciliationStatus.CONFIRMED))
                .build();
    }

    /**
     * OfjClosingTimeExportJob's entry point: sweeps whatever's confirmed-and-unexported for this
     * branch's session, regardless of session status — deliberately NOT {@link #exportDaily}, since
     * the whole point is catching a branch where one agent hasn't balanced yet (session still
     * {@code OPEN}) but another agent's confirmed cash is sitting unexported.
     */
    public void runScheduledClosingExport(OfjSession session) {
        doExport(session.getBranchId(), session, "CSV", ExportTrigger.SCHEDULED_CLOSING_TIME);
    }

    /**
     * The actual CBS posting + export-file bookkeeping, shared by the manual {@link #exportDaily}
     * endpoint, {@link #maybeCloseSession}'s automatic trigger, and the scheduled closing-time job.
     * Returns empty when nothing was actually newly posted (repeated runs over an
     * already-exported session are expected and harmless — see the confirmed-and-unexported
     * query — so this deliberately doesn't create an empty {@link ExportBatch} row every time a
     * re-run finds nothing pending).
     */
    private Optional<ExportBatch> doExport(UUID branchId, OfjSession session, String format, ExportTrigger trigger) {
        int posted = postCollectionsToLedger(branchId, session);
        if (posted == 0) {
            return Optional.empty();
        }

        String fileUri = "export/" + branchId + "/" + session.getBusinessDate() + "." + format.toLowerCase();
        MiddlewareExportAck ack = cbsClientService.submitDailyExport(branchId, fileUri, format).block();

        ExportBatch batch = ExportBatch.builder()
                .id(UUID.randomUUID())
                .ofjId(session.getId())
                .fileUri(fileUri)
                .format(format)
                .trigger(trigger)
                .ackStatus(ack != null && ack.isAcknowledged() ? "ACKNOWLEDGED:" + ack.getAckReference() : "FAILED")
                .build();
        exportBatchRepository.save(batch);
        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.FINANCIAL)
                .eventType("OFJ_EXPORT_SUBMITTED")
                .actorType(trigger == ExportTrigger.MANUAL ? AuditActorType.ADMIN : AuditActorType.SYSTEM)
                .actorLabel(trigger == ExportTrigger.MANUAL ? "Back-Office" : "SYSTEM")
                .branchId(branchId)
                .detailsKey("OFJ_EXPORT_SUBMITTED_DETAIL")
                .detailsParam1(trigger.name())
                .detailsParam2(String.valueOf(posted))
                .build());
        if (trigger == ExportTrigger.SCHEDULED_CLOSING_TIME) {
            applicationEventPublisher.publishEvent(new OfjClosingExportCompletedEvent(
                    OfjClosingExportAlertResponse.builder()
                            .branchId(branchId)
                            .businessDate(session.getBusinessDate())
                            .postedCount(posted)
                            .exportedAt(Instant.now())
                            .build()));
        }
        return Optional.of(batch);
    }

    /**
     * Posts the branch's confirmed-and-unexported cash — both regular {@link Collection} deposits
     * and finalized UC-19 activation fees ({@code ActivationPayment}, real cash the same as a
     * Collection, see {@link ActivationDirectoryService}) — to the CBS ledger, so the mock (and
     * any real adapter) genuinely reflects what clients contributed/paid rather than
     * {@code getBalance}/{@code getHistory} being disconnected from real activity. Returns how many
     * lines were actually posted (0 if there was nothing new).
     */
    private int postCollectionsToLedger(UUID branchId, OfjSession session) {
        List<UUID> activeAgentIds = agentDirectoryService.findActiveAgentIdsByBranch(branchId);
        if (activeAgentIds.isEmpty()) {
            return 0;
        }

        // Posts exactly what THIS session's lines actually reconciled, not "whatever has a
        // collectedAt matching this calendar date" — a collection recorded offline days ago and
        // only just synced keeps its original collectedAt, so a date-window query here would
        // silently never post it even though reconcile() already counted it in digitalTotalXaf.
        List<UUID> lineIds = ofjAgentLineRepository.findByOfjId(session.getId()).stream().map(OfjAgentLine::getId).toList();

        List<Collection> collections = collectionRepository.findByReconciledInLineIdInAndReconciliationStatusAndVoidedAtIsNullAndExportedAtIsNull(
                lineIds, CollectionReconciliationStatus.CONFIRMED);
        List<ActivationCashLine> activationLines = activationDirectoryService.findByReconciledInLineIds(lineIds);
        return postToLedger(collections, activationLines, "ofj-export-" + session.getId());
    }

    /**
     * The shared CBS-posting core both {@link #postCollectionsToLedger} (whole-session) and
     * {@link #exportForAgent} (one agent's own cash) call, so both stamp {@code exportedAt}/
     * {@code cbsTransactionRef} identically instead of duplicating the posting logic. Returns how
     * many lines were actually posted.
     */
    private int postToLedger(List<Collection> collections, List<ActivationCashLine> activationLines, String idempotencyKey) {
        List<MiddlewareCollectionLine> lines = new ArrayList<>(collections.stream()
                .map(collection -> MiddlewareCollectionLine.builder()
                        .collectionId(collection.getId())
                        .memberId(clientDirectoryService.findCbsRef(collection.getClientId()))
                        .amountXaf(collection.getAmountXaf())
                        .collectedAt(collection.getCollectedAt())
                        .build())
                .toList());
        // Collections occupy the front of `lines` (see the loop below, which walks collections
        // first, then activationLines) — activation payments get their own exportedAt/cbsTransactionRef
        // stamped the same way via ActivationDirectoryService#markExported.
        activationLines.stream()
                .map(payment -> MiddlewareCollectionLine.builder()
                        .collectionId(payment.id())
                        .memberId(clientDirectoryService.findCbsRef(payment.clientId()))
                        .amountXaf(payment.amountXaf())
                        .collectedAt(payment.paidAt())
                        .build())
                .forEach(lines::add);

        if (lines.isEmpty()) {
            return 0;
        }

        MiddlewareTransactionPostResult result = cbsClientService.postTransactions(lines, idempotencyKey)
                .doOnError(e -> log.error("Failed to post cash to CBS ledger for {}: {}", idempotencyKey, e.getMessage()))
                .onErrorComplete()
                .block();

        // Positional correlation with `collections`/`activationLines`, not a returned id —
        // MiddlewareTransactionPostResult only carries a flat list of reference strings, in the
        // same order the lines were sent (confirmed against MockCbsAdapter#postTransactions). This
        // is only as reliable as that ordering guarantee holds for whatever adapter is active; a
        // real vendor adapter that doesn't preserve order would need this DTO to carry an explicit
        // collectionId<->reference mapping instead.
        if (result == null || !result.isSuccess() || result.getPostedReferences() == null) {
            return 0;
        }
        Instant exportedAt = Instant.now();
        List<String> refs = result.getPostedReferences();
        int posted = 0;
        for (int i = 0; i < collections.size() && i < refs.size(); i++) {
            Collection collection = collections.get(i);
            collection.setExportedAt(exportedAt);
            collection.setCbsTransactionRef(refs.get(i));
            collectionRepository.save(collection);
            posted++;
        }
        for (int i = collections.size(); i < collections.size() + activationLines.size() && i < refs.size(); i++) {
            ActivationCashLine payment = activationLines.get(i - collections.size());
            activationDirectoryService.markExported(payment.id(), exportedAt, refs.get(i));
            posted++;
        }
        return posted;
    }

    private OfjSession getOrCreateSession(UUID branchId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)
                .orElseGet(() -> {
                    try {
                        return ofjSessionRepository.save(OfjSession.builder()
                                .id(UUID.randomUUID())
                                .branchId(branchId)
                                .businessDate(today)
                                .build());
                    } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
                        // Two branch-summary requests can both miss the findBy... lookup above in
                        // the same instant (no session yet) and both try to create today's first
                        // one; the DB's (branch_id, business_date) unique constraint lets only one
                        // insert win. The loser just re-reads what the winner created instead of
                        // surfacing a 500 for something that isn't actually an error.
                        return ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)
                                .orElseThrow(() -> raceLostToAnotherRequest);
                    }
                });
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
        try {
            doExport(session.getBranchId(), session, "CSV", ExportTrigger.AUTO_ON_CLOSE);
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
        List<Collection> rejected = collectionRepository.findByReconciledInLineIdAndVoidedAtIsNotNull(line.getId());
        long rejectedActualTotalXaf = rejected.stream().mapToLong(Collection::getAmountXaf).sum();
        long rejectedExpectedTotalXaf = 0L;
        if (!rejected.isEmpty()) {
            List<UUID> rejectedIds = rejected.stream().map(Collection::getId).toList();
            rejectedExpectedTotalXaf = collectionRejectionRequestRepository
                    .findByCollectionIdInAndStatus(rejectedIds, CollectionRejectionStatus.APPROVED).stream()
                    .mapToLong(r -> nz(r.getExpectedAmountXaf()))
                    .sum();
        }
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
                .pendingConfirmationCount(collectionRepository.countByReconciledInLineIdAndReconciliationStatusAndVoidedAtIsNull(
                        line.getId(), CollectionReconciliationStatus.PENDING_AGENT_CONFIRMATION))
                .rejectedCount(rejected.size())
                .rejectedActualTotalXaf(rejectedActualTotalXaf)
                .rejectedExpectedTotalXaf(rejectedExpectedTotalXaf)
                .confirmedTotalXaf(collectionRepository.sumByReconciledInLineIdAndReconciliationStatus(
                        line.getId(), CollectionReconciliationStatus.CONFIRMED))
                .confirmedCount(collectionRepository.countByReconciledInLineIdAndReconciliationStatusAndVoidedAtIsNull(
                        line.getId(), CollectionReconciliationStatus.CONFIRMED))
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
                .writtenOffReason(debt.getWrittenOffReason())
                .writtenOffBy(debt.getWrittenOffBy())
                .writtenOffAt(debt.getWrittenOffAt())
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
