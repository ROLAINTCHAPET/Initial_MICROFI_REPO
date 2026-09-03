package com.microfi.transactions.service;

import com.microfi.cbsclient.CbsClientService;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.domain.CollectionRejectionRequest;
import com.microfi.transactions.domain.CollectionRejectionStatus;
import com.microfi.transactions.repository.CollectionRejectionRequestRepository;
import com.microfi.transactions.repository.CollectionRepository;
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
    private final ClientDirectoryService clientDirectoryService;
    private final CbsClientService cbsClientService;
    private final SmsGatewayFactory smsGatewayFactory;

    public CollectionRejectionRequest requestRejection(UUID agentId, UUID collectionId, String reason) {
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

        CollectionRejectionRequest request = CollectionRejectionRequest.builder()
                .id(UUID.randomUUID())
                .collectionId(collectionId)
                .agentId(agentId)
                .reason(reason)
                .build();
        return collectionRejectionRequestRepository.save(request);
    }

    /**
     * Voids the collection unconditionally, then reverses it against the CBS and notifies the
     * client only if it had already been exported (see {@link Collection#getExportedAt()}) — a
     * collection still {@code PENDING_AGENT_CONFIRMATION}/{@code CONFIRMED}-but-not-yet-exported
     * was never posted anywhere the client could see, so there's nothing to reverse or explain.
     */
    public CollectionRejectionRequest approve(UUID requestId, String proofPath, UUID reviewerId) {
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

        if (collection.getExportedAt() != null && collection.getCbsTransactionRef() != null) {
            reverseAndNotifyClient(collection);
        }
        return request;
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
