package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AgentDirectoryServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BranchRepository branchRepository;

    private AgentDirectoryService agentDirectoryService;

    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentDirectoryService = new AgentDirectoryService(agentRepository, passwordEncoder, branchRepository);
        ReflectionTestUtils.setField(agentDirectoryService, "maxFailedTransactionPinAttempts", 3);
        ReflectionTestUtils.setField(agentDirectoryService, "transactionPinLockoutMinutes", 15L);
    }

    @Test
    void updateSyncStatusSetsPendingCount() {
        Agent agent = Agent.builder().id(agentId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDirectoryService.updateSyncStatus(agentId, 5);

        assertThat(agent.getPendingSyncCount()).isEqualTo(5);
    }

    @Test
    void updateSyncStatusAgentNotFoundThrows404() {
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentDirectoryService.updateSyncStatus(agentId, 5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void hasPendingUnsyncedCollectionsTrueWhenAnyAgentHasNonZeroCount() {
        Agent clean = Agent.builder().id(UUID.randomUUID()).pendingSyncCount(0).build();
        Agent dirty = Agent.builder().id(agentId).pendingSyncCount(2).build();
        when(agentRepository.findAllById(List.of(clean.getId(), agentId))).thenReturn(List.of(clean, dirty));

        boolean result = agentDirectoryService.hasPendingUnsyncedCollections(List.of(clean.getId(), agentId));

        assertThat(result).isTrue();
    }

    @Test
    void hasPendingUnsyncedCollectionsFalseWhenAllZero() {
        Agent clean = Agent.builder().id(agentId).pendingSyncCount(0).build();
        when(agentRepository.findAllById(List.of(agentId))).thenReturn(List.of(clean));

        boolean result = agentDirectoryService.hasPendingUnsyncedCollections(List.of(agentId));

        assertThat(result).isFalse();
    }

    @Test
    void hasPendingUnsyncedCollectionsFalseForEmptyList() {
        boolean result = agentDirectoryService.hasPendingUnsyncedCollections(List.of());

        assertThat(result).isFalse();
    }

    @Test
    void verifyTransactionPinSucceedsAndClearsPriorFailedAttempts() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).failedTransactionPinAttempts(2).status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(passwordEncoder.matches("1234", "hashed")).thenReturn(true);
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDirectoryService.verifyTransactionPin(agentId, "1234");

        assertThat(agent.getFailedTransactionPinAttempts()).isZero();
    }

    @Test
    void verifyTransactionPinRejectsWrongPinAndIncrementsCounter() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).failedTransactionPinAttempts(0).status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(passwordEncoder.matches("0000", "hashed")).thenReturn(false);
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> agentDirectoryService.verifyTransactionPin(agentId, "0000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
        assertThat(agent.getFailedTransactionPinAttempts()).isEqualTo(1);
    }

    @Test
    void verifyTransactionPinLocksOutAfterThresholdReached() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).failedTransactionPinAttempts(2).status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(passwordEncoder.matches("0000", "hashed")).thenReturn(false);
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> agentDirectoryService.verifyTransactionPin(agentId, "0000"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(agent.getTransactionPinLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void verifyTransactionPinRejectsWhenLockedOutRegardlessOfPin() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).status(AgentStatus.ACTIVE)
                .transactionPinLockedUntil(Instant.now().plusSeconds(600)).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentDirectoryService.verifyTransactionPin(agentId, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("423");
    }

    @Test
    void verifyTransactionPinRejectsUntilAgentSetsTheirOwnPin() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(true).status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentDirectoryService.verifyTransactionPin(agentId, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void effectiveCeilingPctForAgentReturnsBranchConfiguredValue() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).defaultCeilingPct(150).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).isEqualTo(150);
    }

    @Test
    void effectiveCeilingPctForAgentDefaultsTo100WhenBranchUnconfigured() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).isEqualTo(100);
    }

    @Test
    void effectiveCeilingPctForAgentDefaultsTo100WhenBranchMissing() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.empty());

        assertThat(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).isEqualTo(100);
    }

    @Test
    void isBranchPastCloseTimeTrueWhenNoScheduleConfigured() {
        UUID branchId = UUID.randomUUID();
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(Branch.builder().id(branchId).build()));

        assertThat(agentDirectoryService.isBranchPastCloseTime(branchId)).isTrue();
    }

    @Test
    void isBranchPastCloseTimeFalseBeforeConfiguredCloseTime() {
        UUID branchId = UUID.randomUUID();
        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.systemDefault());
        Branch branch = Branch.builder().id(branchId).closeTime(now.plusHours(2)).timezone(java.time.ZoneId.systemDefault().getId()).build();
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.isBranchPastCloseTime(branchId)).isFalse();
    }

    @Test
    void isBranchPastCloseTimeTrueAfterConfiguredCloseTime() {
        UUID branchId = UUID.randomUUID();
        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.systemDefault());
        Branch branch = Branch.builder().id(branchId).closeTime(now.minusHours(2)).timezone(java.time.ZoneId.systemDefault().getId()).build();
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.isBranchPastCloseTime(branchId)).isTrue();
    }

    @Test
    void isBranchPastCloseTimeThrows404WhenBranchMissing() {
        UUID branchId = UUID.randomUUID();
        when(branchRepository.findById(branchId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentDirectoryService.isBranchPastCloseTime(branchId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void effectiveCeilingPctForAgentUnknownAgentThrows404() {
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentDirectoryService.effectiveCeilingPctForAgent(agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void verifyTransactionPinRejectsWhenAgentNotActive() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).status(AgentStatus.PENDING_CEILING).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentDirectoryService.verifyTransactionPin(agentId, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }
}
