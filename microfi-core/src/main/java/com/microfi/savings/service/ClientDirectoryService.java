package com.microfi.savings.service;

import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.domain.ClientStatus;
import com.microfi.savings.repository.ClientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * {@code savings}'s public contract for other modules that need to resolve a client without
 * reaching into {@link ClientProfileRepository} directly — e.g. {@code transactions.CollectionService}
 * validating the client on a deposit (UC-06/07). Mirrors the
 * {@code authentication.AgentDirectoryService} pattern for cross-module reads.
 */
@Service
@RequiredArgsConstructor
public class ClientDirectoryService {

    private final ClientProfileRepository clientProfileRepository;

    /** Throws 404 if the client doesn't exist, 409 if the local mirror row is inactive. */
    public void requireActiveClient(UUID clientId) {
        ClientProfile client = clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client is not active: " + clientId);
        }
    }
}
