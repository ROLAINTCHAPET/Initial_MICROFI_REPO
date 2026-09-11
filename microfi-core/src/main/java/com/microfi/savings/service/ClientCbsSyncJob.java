package com.microfi.savings.service;

import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.domain.ClientProfile;
import com.microfi.savings.repository.ClientProfileRepository;
import com.microfi.shared.dto.MiddlewareMemberLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background reconciliation of MICROFI's local {@code client_profile} mirror against the real
 * CBS record, by account number ({@code ClientProfile#mfiMemberNo}) — entirely unattended, no
 * admin/branch-manager action involved. Exists for the same recovery case {@code
 * CreateClientRequest} does: a client already exists in the CBS but never made it into MICROFI
 * (an automatic refresh missed them, or an admin registered them by hand from the CBS account
 * number alone before the rest of their details were known). The moment this job finds a CBS
 * member sharing that account number, it overwrites the local row's name/email/phone with the
 * CBS's own values — the CBS is the source of truth for a client's identity, this is only ever a
 * mirror of it — and stamps {@link ClientProfile#getCbsSyncedAt()} so the Back-Office can show
 * which rows are still unconfirmed.
 * <p>
 * Runs against every client on every tick rather than only ones never yet synced: a genuine
 * mirror has to stay current if the CBS record itself changes later, not just resolve once and
 * stop watching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientCbsSyncJob {

    private final ClientProfileRepository clientProfileRepository;
    private final CbsClientService cbsClientService;

    @Scheduled(fixedDelayString = "${client.cbs-sync.check-interval-ms:3600000}")
    @Transactional
    public void syncClientsAgainstCbs() {
        List<ClientProfile> clients = clientProfileRepository.findAll();
        for (ClientProfile client : clients) {
            syncOne(client);
        }
    }

    private void syncOne(ClientProfile client) {
        MiddlewareMemberLookup lookup;
        try {
            lookup = cbsClientService.getMember(client.getMfiMemberNo()).block();
        } catch (Exception e) {
            log.warn("CBS sync lookup failed for client {} ({}): {}", client.getId(), client.getMfiMemberNo(), e.getMessage());
            return;
        }
        if (lookup == null || !lookup.found()) {
            return;
        }
        client.setFullName(lookup.fullName());
        if (lookup.email() != null && !lookup.email().isBlank()) {
            client.setEmail(lookup.email());
        }
        if (lookup.phone() != null && !lookup.phone().isBlank()) {
            client.setPhone(lookup.phone());
        }
        client.setCbsSyncedAt(Instant.now());
        clientProfileRepository.save(client);
    }
}
