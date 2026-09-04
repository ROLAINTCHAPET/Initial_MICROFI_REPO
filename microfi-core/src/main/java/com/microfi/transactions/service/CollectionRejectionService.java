package com.microfi.transactions.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.CollectionReconciliationStatus;
import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.repository.CollectionRejectionRequestRepository;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An agent's request to void one of their own collections for error, and a manager/admin's
 * decision on it — mirrors {@code OfjService#writeOffVarianceDebt}'s "immutable record + separate
 * decision trail" shape (see {@link CollectionRejectionRequest}'s doc), kept as its own service
 * rather than folded into {@link OfjService} since it touches CBS reversal and client
 * notification, neither of which any other OFJ governance action needs.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CollectionRejectionService {

    private final CollectionRejectionRequestRepository collectionRejectionRequestRepository;
    private final CollectionRepository collectionRepository;
    private final OfjAgentLineRepository ofjAgentLineRepository;
    private final ClientDirectoryService clientDirectoryService;
    private final CbsClientService cbsClientService;
    private final SmsGatewayFactory smsGatewayFactory;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;

    public CollectionRejectionRequest requestRejection(UUID agentId, UUID collectionId, String reason, Long expectedAmountXaf) {
        Collection collection = requireCollection(collectionId);
        if (!collection.getAgentId().equals(agentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot request rejection of another agent's collection");
        }
        if (collection.getVoidedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Collection has already been voided");
        }
        collectionRejectionRequestRepository.findByCollectionIdAndStatus(collectionId, CollectionRejectionStatus.PENDING)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A rejection request is already pending for this collection");
                });

        // actualAmountXaf is snapshotted from the collection itself, not left to the agent to
        // retype — the reviewer needs to see exactly what was actually recorded (the "false
        // amount") right next to what the agent claims it should have been.
        CollectionRejectionRequest request = CollectionRejectionRequest.builder()
                .id(UUID.randomUUID())
                .collectionId(collectionId)
                .agentId(agentId)
                .reason(reason)
                .actualAmountXaf(collection.getAmountXaf())
                .expectedAmountXaf(expectedAmountXaf)
                .build();
        return collectionRejectionRequestRepository.save(request);
    }

    /**
     * Voids the collection unconditionally, then reverses it against the CBS and notifies the
     * client only if it had already been exported (see {@link Collection#getExportedAt()}) — a
     * collection still {@code PENDING_AGENT_CONFIRMATION}/{@code CONFIRMED}-but-not-yet-exported
     * was never posted anywhere the client could see, so there's nothing to reverse or explain.
     */
    public CollectionRejectionRequest approve(UUID requestId, String proofPath, UUID reviewerId, String reviewerLabel) {
        CollectionRejectionRequest request = requireOpenRequest(requestId);
        Collection collection = requireCollection(request.getCollectionId());

        Instant now = Instant.now();
        request.setStatus(CollectionRejectionStatus.APPROVED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(now);
        request.setProofPath(proofPath);
        collectionRejectionRequestRepository.save(request);

        collection.setVoidedAt(now);
        collectionRepository.save(collection);

        // The line's collectionsTotalXaf/digitalTotalXaf are running totals stamped once at
        // reconcile() time (see OfjService#reconcile), not recomputed live from Collection rows —
        // an approved rejection must debit them here or the branch's OFJ summary keeps counting
        // cash that's since been voided, whether the agent had already confirmed it or was still
        // waiting to (pendingConfirmationCount is filtered dynamically instead, since it's derived
        // fresh on every read rather than stored). Combined into one update with the sibling
        // requeue below (rather than two separate debits) so the "did digital drop to zero"
        // decision that governs physicalTotalXaf/deltaXaf sees the true final state, not a
        // half-updated one from this collection's debit alone.
        UUID originalLineId = collection.getReconciledInLineId();
        if (originalLineId != null) {
            List<Collection> siblings = requeueUnexportedSiblings(originalLineId, collection.getAgentId(), reviewerId, reviewerLabel);
            debitLine(originalLineId, collection.getAmountXaf() + siblings.stream().mapToLong(Collection::getAmountXaf).sum());
        }

        if (collection.getExportedAt() != null && collection.getCbsTransactionRef() != null) {
            reverseAndNotifyClient(collection);
        }
        return request;
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * Debits exactly {@code amountXaf} from the line's stored collectionsTotalXaf/digitalTotalXaf
     * — and, only once that leaves {@code digitalTotalXaf <= 0} (nothing legitimately confirmed
     * remains on the line at all), also zeroes {@code physicalTotalXaf}/{@code deltaXaf}. Without
     * that second step, a line whose entire digital content was voided/requeued would keep
     * showing its old physical count as if it were still validated against something — a real,
     * confusing bug caught live: a branch's "Validés aujourd'hui" queue kept showing a stale
     * physical total for a line that, after a rejection, had zero collections left reconciled
     * against it. When some OTHER, still-legitimate confirmed content remains on the line
     * (digitalTotalXaf stays positive — a genuinely mixed multi-sweep line), physicalTotalXaf is
     * left alone: there's no reliable way to know which portion of it belongs to the voided/
     * requeued part versus the part that's still valid.
     */
    private void debitLine(UUID lineId, long amountXaf) {
        ofjAgentLineRepository.findById(lineId).ifPresent(line -> {
            long newCollectionsTotal = nz(line.getCollectionsTotalXaf()) - amountXaf;
            long newDigitalTotal = line.getDigitalTotalXaf() - amountXaf;
            line.setCollectionsTotalXaf(newCollectionsTotal);
            line.setDigitalTotalXaf(newDigitalTotal);
            if (newDigitalTotal <= 0) {
                line.setPhysicalTotalXaf(0L);
                line.setDeltaXaf(0L);
            }
            ofjAgentLineRepository.save(line);
        });
    }

    /**
     * BR-Requeue-01: an approved rejection restarts reconciliation for the rest of the batch — the
     * cashier's physical count no longer matches a digital total that includes an error, so the
     * cashier redoes it fresh, exactly like a first-time reconciliation. Every sibling under the
     * same {@code reconciledInLineId} that is neither voided nor already exported goes back to
     * {@code UNRECONCILED} (clearing {@code reconciledInLineId}/{@code reconciliationStatus}/
     * {@code reconciledAt}/{@code confirmedBy}), which also automatically re-occupies the agent's
     * escrow ceiling — {@link CollectionRepository#sumUnreconciledByAgent} filters on {@code
     * reconciledAt IS NULL}, nothing else to change there. Already-exported siblings are left
     * completely untouched: that money is posted to the CBS and final regardless of a different
     * sibling's error. Scoped to the WHOLE line, not "only the collections from the same physical
     * sweep as the voided one" — {@code Collection} carries no per-sweep marker distinguishing one
     * same-day cashier count from another reusing the same {@code OfjAgentLine} row (see
     * {@code OfjService#reconcile}'s find-or-create), so there is no narrower boundary to reset to.
     * Returns the reset siblings so the caller can fold their amount into the same line debit as
     * the rejected collection itself (see {@link #debitLine}).
     */
    private List<Collection> requeueUnexportedSiblings(UUID lineId, UUID agentId, UUID reviewerId, String reviewerLabel) {
        List<Collection> siblings = collectionRepository.findByReconciledInLineId(lineId).stream()
                .filter(c -> c.getVoidedAt() == null && c.getExportedAt() == null)
                .toList();
        if (siblings.isEmpty()) {
            return siblings;
        }
        long resetAmountXaf = siblings.stream().mapToLong(Collection::getAmountXaf).sum();
        for (Collection sibling : siblings) {
            sibling.setReconciledInLineId(null);
            sibling.setReconciliationStatus(CollectionReconciliationStatus.UNRECONCILED);
            sibling.setReconciledAt(null);
            sibling.setConfirmedBy(null);
        }
        collectionRepository.saveAll(siblings);

        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.FINANCIAL)
                .eventType("COLLECTION_REJECTION_SIBLINGS_REQUEUED")
                .actorType(AuditActorType.ADMIN)
                .actorId(reviewerId)
                .actorLabel(reviewerLabel)
                .branchId(agentDirectoryService.requireBranchIdForAgent(agentId))
                .agentId(agentId)
                .detailsKey("COLLECTION_REJECTION_SIBLINGS_REQUEUED_DETAIL")
                .detailsParam1(String.valueOf(siblings.size()))
                .detailsParam2(String.valueOf(resetAmountXaf))
                .build());
        return siblings;
    }

    public CollectionRejectionRequest deny(UUID requestId, String decisionReason, UUID reviewerId) {
        CollectionRejectionRequest request = requireOpenRequest(requestId);
        request.setStatus(CollectionRejectionStatus.DENIED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(Instant.now());
        request.setDecisionReason(decisionReason);
        return collectionRejectionRequestRepository.save(request);
    }

    /** {@code agentIds == null} means unrestricted (ADMIN, global scope) — mirrors AdminSosController's scoping. */
    public List<CollectionRejectionRequest> list(List<UUID> agentIds, CollectionRejectionStatus status) {
        if (agentIds == null) {
            return status == null
                    ? collectionRejectionRequestRepository.findAll(Sort.by(Sort.Direction.DESC, "requestedAt"))
                    : collectionRejectionRequestRepository.findByStatusOrderByRequestedAtDesc(status);
        }
        return status == null
                ? collectionRejectionRequestRepository.findByAgentIdInOrderByRequestedAtDesc(agentIds)
                : collectionRejectionRequestRepository.findByAgentIdInAndStatusOrderByRequestedAtDesc(agentIds, status);
    }

    public CollectionRejectionRequest get(UUID requestId) {
        return collectionRejectionRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rejection request not found: " + requestId));
    }

    public UUID findAgentIdForRequest(UUID requestId) {
        return get(requestId).getAgentId();
    }

    /**
     * Best-effort, same "must not block the decision itself" contract as every other
     * notification/broker call in this app (see SosGeocodePublisher's doc) — a CBS or SMS failure
     * here is logged and swallowed, never re-thrown into the approval that already succeeded.
     */
    private void reverseAndNotifyClient(Collection collection) {
        try {
            cbsClientService.reverseTransaction(collection.getCbsTransactionRef(), "collection-reject-" + collection.getId()).block();
        } catch (Exception e) {
            log.error("CBS reversal failed for collection {}: {}", collection.getId(), e.getMessage());
        }
        try {
            String phone = clientDirectoryService.findPhone(collection.getClientId());
            String message = "Une transaction de " + collection.getAmountXaf()
                    + " XAF a ete annulee suite a une erreur de l'agent. Contactez votre agence pour toute question.";
            smsGatewayFactory.getActiveGateway().send(phone, message).block();
        } catch (Exception e) {
            log.error("Client notification failed for voided collection {}: {}", collection.getId(), e.getMessage());
        }
    }

    private CollectionRejectionRequest requireOpenRequest(UUID id) {
        CollectionRejectionRequest request = collectionRejectionRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rejection request not found: " + id));
        if (request.getStatus() != CollectionRejectionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rejection request already decided");
        }
        return request;
    }

    private Collection requireCollection(UUID collectionId) {
        return collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found: " + collectionId));
    }
}
