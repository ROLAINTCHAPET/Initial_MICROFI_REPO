package com.microfi.transactions.service;

import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.transactions.domain.CollectionConfirmedBy;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionConfirmationExpiryJobTest {

    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private OfjAgentLineRepository ofjAgentLineRepository;
    @Mock
    private AgentDirectoryService agentDirectoryService;
    @Mock
    private AuditService auditService;

    private CollectionConfirmationExpiryJob job;

    private final UUID agentId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        job = new CollectionConfirmationExpiryJob(collectionRepository, ofjAgentLineRepository, agentDirectoryService, auditService);
        ReflectionTestUtils.setField(job, "expiryHours", 48L);
        when(agentDirectoryService.requireBranchIdForAgent(agentId)).thenReturn(branchId);
    }

    @Test
    void autoConfirmsLinesStaleyPastTheTimeout() {
        UUID lineId = UUID.randomUUID();
        OfjAgentLine stale = OfjAgentLine.builder().id(lineId).ofjId(UUID.randomUUID()).agentId(agentId)
                .lastCountedAt(Instant.now().minus(72, ChronoUnit.HOURS)).build();
        when(collectionRepository.findDistinctPendingConfirmationLineIds()).thenReturn(List.of(lineId));
        when(ofjAgentLineRepository.findAllById(List.of(lineId))).thenReturn(List.of(stale));
        when(collectionRepository.markAgentConfirmed(eq(lineId), any(), eq(CollectionConfirmedBy.SYSTEM_AUTO_EXPIRY))).thenReturn(4);

        job.expireStalePendingConfirmations();

        verify(collectionRepository).markAgentConfirmed(eq(lineId), any(Instant.class), eq(CollectionConfirmedBy.SYSTEM_AUTO_EXPIRY));
        verify(auditService).record(any(AuditLogEntry.class));
    }

    @Test
    void leavesLinesAloneWhenStillWithinTheTimeoutWindow() {
        UUID lineId = UUID.randomUUID();
        OfjAgentLine fresh = OfjAgentLine.builder().id(lineId).ofjId(UUID.randomUUID()).agentId(agentId)
                .lastCountedAt(Instant.now().minus(2, ChronoUnit.HOURS)).build();
        when(collectionRepository.findDistinctPendingConfirmationLineIds()).thenReturn(List.of(lineId));
        when(ofjAgentLineRepository.findAllById(List.of(lineId))).thenReturn(List.of(fresh));

        job.expireStalePendingConfirmations();

        verify(collectionRepository, never()).markAgentConfirmed(any(), any(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void doesNothingWhenNoLinesAwaitingConfirmation() {
        when(collectionRepository.findDistinctPendingConfirmationLineIds()).thenReturn(List.of());

        job.expireStalePendingConfirmations();

        verify(ofjAgentLineRepository, never()).findAllById(any());
        verify(auditService, never()).record(any());
    }

    @Test
    void doesNotAuditWhenMarkAgentConfirmedUpdatesNoRows() {
        UUID lineId = UUID.randomUUID();
        OfjAgentLine stale = OfjAgentLine.builder().id(lineId).ofjId(UUID.randomUUID()).agentId(agentId)
                .lastCountedAt(Instant.now().minus(72, ChronoUnit.HOURS)).build();
        when(collectionRepository.findDistinctPendingConfirmationLineIds()).thenReturn(List.of(lineId));
        when(ofjAgentLineRepository.findAllById(List.of(lineId))).thenReturn(List.of(stale));
        when(collectionRepository.markAgentConfirmed(eq(lineId), any(), any())).thenReturn(0);

        job.expireStalePendingConfirmations();

        verify(auditService, never()).record(any());
    }
}
