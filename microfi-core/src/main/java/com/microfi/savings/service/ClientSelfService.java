package com.microfi.savings.service;

import com.microfi.savings.domain.AccessToken;
import com.microfi.savings.domain.AccessTokenStatus;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.AccessTokenRepository;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.shared.dto.ClientProfileSelfResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/** UC-20/21/22 — client self-service reads. All read-only; nothing here ever blocks on token expiry. */
@Service
@RequiredArgsConstructor
public class ClientSelfService {

    private final ClientProfileRepository clientProfileRepository;
    private final AccessTokenRepository accessTokenRepository;

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
                .tokenStatus(tokenStatus)
                .tokenExpiresAt(tokenExpiresAt)
                .build();
    }

    /** Resolves the CBS reference to feed into a downstream {@code cbsclient} call (FR-21/FR-22). */
    public String getCbsRef(UUID clientId) {
        return requireClient(clientId).getCbsRef();
    }

    private ClientProfile requireClient(UUID clientId) {
        return clientProfileRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + clientId));
    }
}
