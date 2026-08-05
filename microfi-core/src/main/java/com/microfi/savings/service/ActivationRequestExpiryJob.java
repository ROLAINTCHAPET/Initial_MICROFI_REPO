package com.microfi.savings.service;

import com.microfi.savings.domain.ActivationRequest;
import com.microfi.savings.domain.ActivationRequestStatus;
import com.microfi.savings.repository.ActivationRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Auto-resolves the other half of the "no escape valve" problem an admin's manual
 * {@code ClientActivationService#cancelActivationRequest} only covers if someone notices and acts:
 * a gate nobody is watching (client never confirms, agent never gets told to check) would otherwise
 * block that agent from collecting any cash forever. Runs periodically, expires anything that's
 * been {@code PENDING} longer than the configured timeout — agent-registered or client-paid-only,
 * either way {@link ActivationRequestRepository#findByStatusAndCreatedAtBefore} catches it
 * regardless of which side (if either) is still missing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivationRequestExpiryJob {

    private final ActivationRequestRepository activationRequestRepository;

    @Value("${client.activation.pending-timeout-hours:24}")
    private long pendingTimeoutHours;

    @Scheduled(fixedDelayString = "${client.activation.expiry-check-interval-ms:3600000}")
    @Transactional
    public void expireStalePendingRequests() {
        Instant cutoff = Instant.now().minus(pendingTimeoutHours, ChronoUnit.HOURS);
        List<ActivationRequest> stale = activationRequestRepository.findByStatusAndCreatedAtBefore(ActivationRequestStatus.PENDING, cutoff);
        if (stale.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        String reason = "Automatically expired after " + pendingTimeoutHours + "h with no confirmation from the other party";
        stale.forEach(request -> {
            request.setStatus(ActivationRequestStatus.EXPIRED);
            request.setCancelledAt(now);
            request.setCancelReason(reason);
        });
        activationRequestRepository.saveAll(stale);
        log.info("Expired {} stale pending activation request(s) older than {}h", stale.size(), pendingTimeoutHours);
    }
}
