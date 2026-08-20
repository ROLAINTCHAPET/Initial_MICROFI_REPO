package com.microfi.transactions.service;

import com.microfi.transactions.domain.Collection;
import com.microfi.transactions.repository.CollectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
                collection.getAmountXaf(), collection.getLocationName(), collection.getCollectedAt());
    }
}
