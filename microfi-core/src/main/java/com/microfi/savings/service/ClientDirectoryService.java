package com.microfi.savings.service;

import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.domain.ClientStatus;
import com.microfi.savings.repository.ClientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /** Resolves the CBS reference a collection needs to be posted against (throws 404 if the client doesn't exist). */
    public String findCbsRef(UUID clientId) {
        return clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId))
                .getCbsRef();
    }

    /** Resolves the phone number to send the FR-09 confirmation SMS to (throws 404 if the client doesn't exist). */
    public String findPhone(UUID clientId) {
        return clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId))
                .getPhone();
    }

    /** UC-09 Bluetooth thermal receipt template's "Client ID" / "Name" lines. */
    public ClientReceiptInfo findReceiptInfo(UUID clientId) {
        ClientProfile client = clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
        return new ClientReceiptInfo(client.getMfiMemberNo(), client.getFullName());
    }

    public record ClientReceiptInfo(String mfiMemberNo, String fullName) {
    }

    /** Batch name resolution for a collections list (mobile Recent Collections/History) — never throws on a missing id. */
    public Map<UUID, String> findFullNames(Collection<UUID> clientIds) {
        return clientProfileRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(ClientProfile::getId, ClientProfile::getFullName, (a, b) -> a));
    }
}
