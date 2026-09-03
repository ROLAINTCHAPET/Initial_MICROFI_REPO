package com.microfi.transactions.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.transactions.domain.CollectionConfirmedBy;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
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
 * Auto-resolves the same "no escape valve" problem {@code ActivationRequestExpiryJob} exists for:
 * a gate nobody is watching (an agent who never opens the app, is on leave, or simply forgets)
 * would otherwise leave their own cash-in-hand permanently occupying their escrow ceiling. Filters
 * candidate lines in Java rather than in the query — {@code OfjAgentLine} has no direct link to
 * {@code Collection}'s reconciliation status, so {@code CollectionRepository
 * #findDistinctPendingConfirmationLineIds} finds the candidates and this job checks each one's own
 * {@code lastCountedAt} age, avoiding a speculative cross-entity JPQL join.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectionConfirmationExpiryJob {

    private final CollectionRepository collectionRepository;
    private final OfjAgentLineRepository ofjAgentLineRepository;
    private final AgentDirectoryService agentDirectoryService;
    private final AuditService auditService;

    @Value("${collection.confirmation.expiry-hours:48}")
    private long expiryHours;

    @Scheduled(fixedDelayString = "${collection.confirmation.expiry-check-interval-ms:3600000}")
    @Transactional
    public void expireStalePendingConfirmations() {
        List<java.util.UUID> candidateLineIds = collectionRepository.findDistinctPendingConfirmationLineIds();
        if (candidateLineIds.isEmpty()) {
            return;
        }
        Instant cutoff = Instant.now().minus(expiryHours, ChronoUnit.HOURS);
        List<OfjAgentLine> stale = ofjAgentLineRepository.findAllById(candidateLineIds).stream()
                .filter(line -> line.getLastCountedAt() != null && line.getLastCountedAt().isBefore(cutoff))
                .toList();
        if (stale.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        int total = 0;
        for (OfjAgentLine line : stale) {
            int updated = collectionRepository.markAgentConfirmed(line.getId(), now, CollectionConfirmedBy.SYSTEM_AUTO_EXPIRY);
            if (updated > 0) {
                total += updated;
                auditService.record(AuditLogEntry.builder()
                        .category(AuditCategory.FINANCIAL)
                        .eventType("COLLECTION_RECONCILIATION_CONFIRMED")
                        .actorType(AuditActorType.SYSTEM)
                        .branchId(agentDirectoryService.requireBranchIdForAgent(line.getAgentId()))
                        .agentId(line.getAgentId())
                        .detailsKey("COLLECTION_RECONCILIATION_CONFIRMED_DETAIL")
                        .detailsParam1("SYSTEM_AUTO_EXPIRY")
                        .build());
            }
        }
        log.info("Auto-confirmed {} collection(s) across {} stale reconciliation line(s) older than {}h", total, stale.size(), expiryHours);
    }
}
