package com.microfi.savings.service;

import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.domain.ClientStatus;
import com.microfi.savings.repository.ClientProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
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

    /**
     * {@code CollectionService.recordCollection}'s "portefeuille client" gate, only invoked when
     * the agent's branch opts into {@code Branch#requireClientPortfolio}. A client with no
     * assigned agent yet (assignedAgentId null) is deliberately let through regardless of caller:
     * the restriction only narrows an existing assignment, it never blocks an untouched client.
     */
    public void requireInPortfolio(UUID agentId, UUID clientId) {
        ClientProfile client = clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
        UUID assignedAgentId = client.getAssignedAgentId();
        if (assignedAgentId != null && !assignedAgentId.equals(agentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This client belongs to another agent's portfolio — ask your branch manager to reassign them before collecting here");
        }
    }

    /**
     * Sets this client's portfolio-owning agent — called both by a manager/admin reassigning a
     * client by hand (see ClientController) and automatically once an agent's sponsored activation
     * completes (see ClientActivationService#confirmPayment, which always overwrites: the most
     * recently completed activation's agent becomes the current owner). {@code agentId} may be
     * null to clear the assignment.
     */
    public void setAssignedAgent(UUID clientId, UUID agentId) {
        ClientProfile client = clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
        client.setAssignedAgentId(agentId);
        clientProfileRepository.save(client);
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

    /** Bulk account-number lookup for a CSV/audit export spanning many clients — mirrors {@link #findFullNames}'s exact shape, kept separate since most callers of one don't need the other. */
    public Map<UUID, String> findMfiMemberNos(Collection<UUID> clientIds) {
        return clientProfileRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(ClientProfile::getId, ClientProfile::getMfiMemberNo, (a, b) -> a));
    }

    /** For a branch-scoped broadcast SMS blast — mirrors AgentDirectoryService#findAgentPhonesByBranch. */
    public List<String> findClientPhonesByBranch(UUID branchId) {
        return clientProfileRepository.findByBranchId(branchId).stream()
                .map(ClientProfile::getPhone)
                .filter(phone -> phone != null && !phone.isBlank())
                .toList();
    }

    /** For a network-wide broadcast SMS blast (ADMIN only). */
    public List<String> findAllClientPhones() {
        return clientProfileRepository.findAll().stream()
                .map(ClientProfile::getPhone)
                .filter(phone -> phone != null && !phone.isBlank())
                .toList();
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
