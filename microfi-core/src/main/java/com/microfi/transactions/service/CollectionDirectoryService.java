package com.microfi.transactions.service;

import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.DenominationLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * {@code transactions}'s public contract for other modules that need to resolve a collection
 * without reaching into {@link CollectionRepository} directly — e.g. {@code notifications}
 * composing the FR-09 confirmation SMS for a given collection. Mirrors
 * {@code savings.ClientDirectoryService}'s pattern for cross-module reads.
 */
@Service
@RequiredArgsConstructor
public class CollectionDirectoryService {

    private final CollectionRepository collectionRepository;
    private final DenominationLineRepository denominationLineRepository;

    public CollectionSummary findById(UUID collectionId) {
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection not found: " + collectionId));
        return toSummary(collection);
    }

    /**
     * A client's own recent collections, newest first — sourced from MICROFI's own record, not
     * the CBS. The CBS ledger (savings.ClientSelfService#getCbsRef + cbsclient history) only
     * reflects a collection once the end-of-day export posts it (OfjService), so a client
     * checking right after handing over cash would otherwise see nothing until day-end. This is
     * what powers the client app's immediate "we got your deposit" view.
     */
    public List<CollectionSummary> findRecentByClient(UUID clientId) {
        return collectionRepository.findTop50ByClientIdOrderByCollectedAtDesc(clientId).stream()
                .map(this::toSummary)
                .toList();
    }

    private CollectionSummary toSummary(Collection collection) {
        return new CollectionSummary(collection.getId(), collection.getAgentId(), collection.getClientId(),
                collection.getAmountXaf(), collection.getLocationName(), collection.getCollectedAt(),
                collection.getLat(), collection.getLon(), collection.getDeviceTxId());
    }

    /** FR-08 denomination breakdown for the receipt template, highest face value first. */
    public List<DenominationLineView> findDenominationLines(UUID collectionId) {
        return denominationLineRepository.findByCollectionId(collectionId).stream()
                .map(line -> new DenominationLineView(line.getFaceValueXaf(), line.getQuantity()))
                .sorted(Comparator.comparingLong(DenominationLineView::faceValueXaf).reversed())
                .toList();
    }

    public record DenominationLineView(long faceValueXaf, int quantity) {
    }
}
