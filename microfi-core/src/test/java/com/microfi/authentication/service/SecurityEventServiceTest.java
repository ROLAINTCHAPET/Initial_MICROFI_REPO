package com.microfi.authentication.service;

import com.microfi.audit.service.AuditLogEntry;
import com.microfi.audit.service.AuditService;
import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.SecurityEvent;
import com.microfi.authentication.domain.SecurityEventType;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.SecurityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityEventServiceTest {

    @Mock
    private SecurityEventRepository securityEventRepository;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private AgentDirectoryService agentDirectoryService;

    private SecurityEventService securityEventService;

    private final UUID agentId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        securityEventService = new SecurityEventService(securityEventRepository, agentRepository, auditService, agentDirectoryService);
        when(securityEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(Agent.builder().id(agentId).username("agt.dupont").build()));
    }

    @Test
    void raisePersistsEventAndAuditsIt() {
        SecurityEvent saved = securityEventService.raise(agentId, branchId, SecurityEventType.INSTALLATION_MISMATCH, "expected=A got=B");

        assertThat(saved.getAgentId()).isEqualTo(agentId);
        assertThat(saved.getType()).isEqualTo(SecurityEventType.INSTALLATION_MISMATCH);
        assertThat(saved.getResolvedAt()).isNull();

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().getDetailsParam1()).isEqualTo("INSTALLATION_MISMATCH");
        assertThat(captor.getValue().getActorLabel()).isEqualTo("agt.dupont");
    }

    @Test
    void raiseFlipsAgentToReconciliationRequiredForInstallationMismatch() {
        securityEventService.raise(agentId, branchId, SecurityEventType.INSTALLATION_MISMATCH, "detail");

        verify(agentDirectoryService).flipToReconciliationRequired(agentId);
    }

    @Test
    void raiseFlipsAgentToReconciliationRequiredForDeviceMismatch() {
        securityEventService.raise(agentId, branchId, SecurityEventType.DEVICE_MISMATCH, "detail");

        verify(agentDirectoryService).flipToReconciliationRequired(agentId);
    }

    @Test
    void raiseDoesNotFlipAgentForChainAnomalyTypes() {
        securityEventService.raise(agentId, branchId, SecurityEventType.COUNTER_GAP, "detail");
        securityEventService.raise(agentId, branchId, SecurityEventType.BAD_PREVIOUS_HASH, "detail");
        securityEventService.raise(agentId, branchId, SecurityEventType.BAD_SIGNATURE, "detail");
        securityEventService.raise(agentId, branchId, SecurityEventType.DEVICE_NOT_AUTHORIZED, "detail");

        verify(agentDirectoryService, never()).flipToReconciliationRequired(any());
    }

    @Test
    void resolveStampsResolutionFields() {
        SecurityEvent event = SecurityEvent.builder().id(UUID.randomUUID()).agentId(agentId).type(SecurityEventType.INSTALLATION_MISMATCH).build();
        UUID adminId = UUID.randomUUID();
        when(securityEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        SecurityEvent resolved = securityEventService.resolve(event.getId(), adminId, "Cleared by reset");

        assertThat(resolved.getResolvedAt()).isNotNull();
        assertThat(resolved.getResolvedByAdminUserId()).isEqualTo(adminId);
        assertThat(resolved.getResolutionReason()).isEqualTo("Cleared by reset");
    }

    @Test
    void resolveUnknownEventThrows404() {
        UUID unknownId = UUID.randomUUID();
        when(securityEventRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> securityEventService.resolve(unknownId, UUID.randomUUID(), "reason"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void resolveAllOpenForAgentResolvesEveryOpenEvent() {
        SecurityEvent open1 = SecurityEvent.builder().id(UUID.randomUUID()).agentId(agentId).build();
        SecurityEvent open2 = SecurityEvent.builder().id(UUID.randomUUID()).agentId(agentId).build();
        when(securityEventRepository.findByAgentIdAndResolvedAtIsNull(agentId)).thenReturn(List.of(open1, open2));

        securityEventService.resolveAllOpenForAgent(agentId, UUID.randomUUID(), "Cleared by device-binding reset");

        assertThat(open1.getResolvedAt()).isNotNull();
        assertThat(open2.getResolvedAt()).isNotNull();
        verify(securityEventRepository, times(2)).save(any());
    }

    @Test
    void listOpenNetworkWideWhenBranchIdNull() {
        securityEventService.listOpen(null);

        verify(securityEventRepository).findByResolvedAtIsNullOrderByCreatedAtDesc();
        verify(securityEventRepository, never()).findByBranchIdAndResolvedAtIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    void listOpenBranchScopedWhenBranchIdProvided() {
        securityEventService.listOpen(branchId);

        verify(securityEventRepository).findByBranchIdAndResolvedAtIsNullOrderByCreatedAtDesc(branchId);
    }
}
