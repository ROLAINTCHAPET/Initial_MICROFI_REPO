package com.microfi.transactions.service;

import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.transactions.domain.OfjSession;
import com.microfi.transactions.repository.OfjSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * The "closing time" catch-all mentioned alongside the agent's own "End My Day" action — an agent
 * who never taps it (forgets, is on leave, doesn't have the app updated) would otherwise leave
 * their confirmed cash sitting unexported indefinitely. Runs on a short poll rather than a single
 * daily cron trigger (mirrors {@link CollectionConfirmationExpiryJob}'s shape) so it naturally
 * respects each branch's own configured {@link Branch#getCloseTime()}/{@link Branch#getTimezone()}
 * instead of one global cutoff. No per-branch "already triggered today" marker is needed: {@link
 * OfjService#runScheduledClosingExport} is already a safe no-op once nothing confirmed-and-unexported
 * remains (see CollectionRepository's confirmed-and-unexported query), so re-checking a branch that
 * already exported everything costs one cheap empty query, not a repeated CBS post.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfjClosingTimeExportJob {

    private final BranchRepository branchRepository;
    private final OfjSessionRepository ofjSessionRepository;
    private final OfjService ofjService;

    @Scheduled(fixedDelayString = "${ofj.closing-export.check-interval-ms:900000}")
    @Transactional
    public void exportBranchesPastClosingTime() {
        List<Branch> configuredBranches = branchRepository.findByCloseTimeIsNotNullAndTimezoneIsNotNull();
        if (configuredBranches.isEmpty()) {
            return;
        }

        int triggered = 0;
        for (Branch branch : configuredBranches) {
            ZoneId zone = zoneIdOrNull(branch.getTimezone());
            if (zone == null) {
                continue;
            }
            LocalTime nowLocal = Instant.now().atZone(zone).toLocalTime();
            if (nowLocal.isBefore(branch.getCloseTime())) {
                continue;
            }
            LocalDate today = LocalDate.now(zone);
            Optional<OfjSession> session = ofjSessionRepository.findByBranchIdAndBusinessDate(branch.getId(), today);
            if (session.isEmpty()) {
                continue;
            }
            ofjService.runScheduledClosingExport(session.get());
            triggered++;
        }
        if (triggered > 0) {
            log.info("Scheduled closing-time export checked {} branch(es) past their closing time", triggered);
        }
    }

    /** Same "garbage timezone string treated as unconfigured" precedent as AgentDirectoryService#zoneIdOrNull. */
    private ZoneId zoneIdOrNull(String timezone) {
        if (timezone == null) {
            return null;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
