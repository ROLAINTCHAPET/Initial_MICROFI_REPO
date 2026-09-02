package com.microfi.audit.service;

import com.microfi.audit.domain.AuditActorType;
import com.microfi.audit.domain.AuditCategory;
import com.microfi.audit.domain.AuditLog;
import com.microfi.audit.domain.AuditStatus;
import com.microfi.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    private final UUID branchId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    void recordSavesEntryWithGeneratedIdAndTimestamp() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("AGENT_SUSPENDED")
                .actorType(AuditActorType.ADMIN)
                .actorId(UUID.randomUUID())
                .actorLabel("admin")
                .branchId(branchId)
                .agentId(agentId)
                .details("Suspended for cause")
                .build());

        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOccurredAt()).isNotNull();
        assertThat(saved.getCategory()).isEqualTo(AuditCategory.SECURITY);
        assertThat(saved.getEventType()).isEqualTo("AGENT_SUSPENDED");
        assertThat(saved.getActorType()).isEqualTo(AuditActorType.ADMIN);
        assertThat(saved.getBranchId()).isEqualTo(branchId);
        assertThat(saved.getAgentId()).isEqualTo(agentId);
        assertThat(saved.getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    void recordDefaultsStatusToSuccessWhenNotSpecified() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("ADMIN_LOGIN")
                .actorType(AuditActorType.ADMIN)
                .actorLabel("admin")
                .details("Login succeeded")
                .build());

        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    void recordSwallowsRepositoryFailureSoCallerIsNeverBroken() {
        doThrow(new RuntimeException("DB down")).when(auditLogRepository).save(any());

        auditService.record(AuditLogEntry.builder()
                .category(AuditCategory.SECURITY)
                .eventType("ADMIN_LOGIN")
                .actorType(AuditActorType.ADMIN)
                .actorLabel("admin")
                .status(AuditStatus.FAILED)
                .details("Login failed: bad password")
                .build());
        // No exception propagated — the audit write must never fail the action it's logging.
    }

    @Test
    void searchDelegatesToRepositoryWithGivenCriteria() {
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();
        List<AuditLog> expected = List.of(AuditLog.builder().id(UUID.randomUUID()).build());
        when(auditLogRepository.search(from, to, branchId, AuditCategory.SECURITY, AuditActorType.ADMIN))
                .thenReturn(expected);

        List<AuditLog> result = auditService.search(from, to, branchId, AuditCategory.SECURITY, AuditActorType.ADMIN);

        assertThat(result).isEqualTo(expected);
        verify(auditLogRepository).search(from, to, branchId, AuditCategory.SECURITY, AuditActorType.ADMIN);
    }
}
