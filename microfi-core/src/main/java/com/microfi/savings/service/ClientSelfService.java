package com.microfi.savings.service;

import com.microfi.savings.domain.AccessToken;
import com.microfi.savings.domain.AccessTokenStatus;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.AccessTokenRepository;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.notifications.service.MfiSettingsService;
import com.microfi.shared.dto.ClientProfileSelfResponse;
import com.microfi.shared.dto.ClientRecentCollectionResponse;
import com.microfi.transactions.service.CollectionDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** UC-20/21/22 — client self-service reads. All read-only; nothing here ever blocks on token expiry. */
@Service
@RequiredArgsConstructor
public class ClientSelfService {

    private final ClientProfileRepository clientProfileRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final CollectionDirectoryService collectionDirectoryService;
    private final MfiSettingsService mfiSettingsService;

    public ClientProfileSelfResponse getProfile(UUID clientId) {
        ClientProfile client = requireClient(clientId);
        AccessToken latest = accessTokenRepository.findFirstByClientIdAndStatusOrderByIssuedAtDesc(clientId, AccessTokenStatus.ACTIVE)
                .orElse(null);

        String tokenStatus = "NONE";
        Instant tokenExpiresAt = null;
        if (latest != null) {
            tokenStatus = latest.getExpiresAt().isAfter(Instant.now()) ? "ACTIVE" : "EXPIRED";
            tokenExpiresAt = latest.getExpiresAt();
        }

        return ClientProfileSelfResponse.builder()
                .id(client.getId())
                .mfiMemberNo(client.getMfiMemberNo())
                .fullName(client.getFullName())
                .phone(client.getPhone())
                .branchId(client.getBranchId())
                .mfiName(mfiSettingsService.getName())
                .tokenStatus(tokenStatus)
                .tokenExpiresAt(tokenExpiresAt)
                .build();
    }

    /** Resolves the CBS reference to feed into a downstream {@code cbsclient} call (FR-21/FR-22). */
    public String getCbsRef(UUID clientId) {
        return requireClient(clientId).getCbsRef();
    }

    /** See CollectionDirectoryService#findRecentByClient — visible before the CBS-backed history is. */
    public List<ClientRecentCollectionResponse> getRecentCollections(UUID clientId) {
        return collectionDirectoryService.findRecentByClient(clientId).stream()
                .map(c -> ClientRecentCollectionResponse.builder()
                        .id(c.id())
                        .amountXaf(c.amountXaf())
                        .locationName(c.locationName())
                        .collectedAt(c.collectedAt())
                        .build())
                .toList();
    }

    private ClientProfile requireClient(UUID clientId) {
        return clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
    }
}
