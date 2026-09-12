package com.microfi.authentication.service;

import com.microfi.authentication.domain.Agent;
import com.microfi.authentication.domain.AgentInstallationBinding;
import com.microfi.authentication.domain.AgentStatus;
import com.microfi.authentication.domain.Branch;
import com.microfi.authentication.repository.AgentRepository;
import com.microfi.authentication.repository.BranchRepository;
import com.microfi.transactions.domain.CollectionOrigin;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDirectoryServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private InstallationBindingService installationBindingService;

    private AgentDirectoryService agentDirectoryService;

    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentDirectoryService = new AgentDirectoryService(agentRepository, passwordEncoder, branchRepository, installationBindingService);
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
    void effectiveCeilingPctForAgentUnknownAgentThrows404() {
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentDirectoryService.effectiveCeilingPctForAgent(agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void effectiveRequireClientActivationForAgentReturnsBranchConfiguredValue() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).requireClientActivation(true).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.effectiveRequireClientActivationForAgent(agentId)).isTrue();
    }

    @Test
    void effectiveRequireClientActivationForAgentDefaultsToFalseWhenBranchUnconfigured() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.effectiveRequireClientActivationForAgent(agentId)).isFalse();
    }

    @Test
    void effectiveRequireClientActivationForAgentDefaultsToFalseWhenBranchMissing() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.empty());

        assertThat(agentDirectoryService.effectiveRequireClientActivationForAgent(agentId)).isFalse();
    }

    @Test
    void effectiveRequireClientActivationForAgentUnknownAgentThrows404() {
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentDirectoryService.effectiveRequireClientActivationForAgent(agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void effectiveRequireClientPortfolioForAgentReturnsBranchConfiguredValue() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).requireClientPortfolio(true).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.effectiveRequireClientPortfolioForAgent(agentId)).isTrue();
    }

    @Test
    void effectiveRequireClientPortfolioForAgentDefaultsToFalseWhenBranchUnconfigured() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        assertThat(agentDirectoryService.effectiveRequireClientPortfolioForAgent(agentId)).isFalse();
    }

    @Test
    void effectiveRequireClientPortfolioForAgentDefaultsToFalseWhenBranchMissing() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.empty());

        assertThat(agentDirectoryService.effectiveRequireClientPortfolioForAgent(agentId)).isFalse();
    }

    @Test
    void effectiveRequireClientPortfolioForAgentUnknownAgentThrows404() {
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentDirectoryService.effectiveRequireClientPortfolioForAgent(agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void addCarriedPhysicalXafSetsItFromNull() {
        Agent agent = Agent.builder().id(agentId).carriedPhysicalXaf(null).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        agentDirectoryService.addCarriedPhysicalXaf(agentId, 2000L);

        assertThat(agent.getCarriedPhysicalXaf()).isEqualTo(2000L);
        verify(agentRepository).save(agent);
    }

    @Test
    void addCarriedPhysicalXafAccumulatesOnTopOfAnExistingCarry() {
        Agent agent = Agent.builder().id(agentId).carriedPhysicalXaf(2000L).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        agentDirectoryService.addCarriedPhysicalXaf(agentId, 500L);

        assertThat(agent.getCarriedPhysicalXaf()).isEqualTo(2500L);
    }

    @Test
    void consumeCarriedPhysicalXafReturnsAndClearsIt() {
        Agent agent = Agent.builder().id(agentId).carriedPhysicalXaf(2000L).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        long consumed = agentDirectoryService.consumeCarriedPhysicalXaf(agentId);

        assertThat(consumed).isEqualTo(2000L);
        assertThat(agent.getCarriedPhysicalXaf()).isNull();
        verify(agentRepository).save(agent);
    }

    @Test
    void consumeCarriedPhysicalXafReturnsZeroWhenNoneOutstanding() {
        Agent agent = Agent.builder().id(agentId).carriedPhysicalXaf(null).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        long consumed = agentDirectoryService.consumeCarriedPhysicalXaf(agentId);

        assertThat(consumed).isEqualTo(0L);
        verify(agentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void verifyTransactionPinRejectsWhenAgentNotActive() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).status(AgentStatus.PENDING_CEILING).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentDirectoryService.verifyTransactionPin(agentId, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void requireWithinScheduleWindowAllowsCollectionInsideWindow() {
        UUID branchId = UUID.randomUUID();
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalTime collectedLocalTime = java.time.LocalTime.now(zone);
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1")
                .openTime(collectedLocalTime.minusHours(2)).closeTime(collectedLocalTime.plusHours(2)).timezone(zone.getId()).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        agentDirectoryService.requireWithinScheduleWindow(agentId, collectedLocalTime.atDate(java.time.LocalDate.now(zone)).atZone(zone).toInstant());
    }

    @Test
    void requireWithinScheduleWindowRejectsWhenCollectedAfterClosingTime() {
        UUID branchId = UUID.randomUUID();
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalTime collectedLocalTime = java.time.LocalTime.now(zone);
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1")
                .openTime(collectedLocalTime.minusHours(3)).closeTime(collectedLocalTime.minusHours(1)).timezone(zone.getId()).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        java.time.Instant collectedAt = collectedLocalTime.atDate(java.time.LocalDate.now(zone)).atZone(zone).toInstant();
        assertThatThrownBy(() -> agentDirectoryService.requireWithinScheduleWindow(agentId, collectedAt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void requireWithinScheduleWindowRejectsWhenCollectedBeforeOpeningTime() {
        UUID branchId = UUID.randomUUID();
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalTime collectedLocalTime = java.time.LocalTime.now(zone);
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1")
                .openTime(collectedLocalTime.plusHours(1)).closeTime(collectedLocalTime.plusHours(3)).timezone(zone.getId()).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        java.time.Instant collectedAt = collectedLocalTime.atDate(java.time.LocalDate.now(zone)).atZone(zone).toInstant();
        assertThatThrownBy(() -> agentDirectoryService.requireWithinScheduleWindow(agentId, collectedAt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void requireWithinScheduleWindowAllowsWhenBranchHasNoScheduleConfigured() {
        UUID branchId = UUID.randomUUID();
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1").build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        agentDirectoryService.requireWithinScheduleWindow(agentId, java.time.Instant.now());
    }

    @Test
    void requireWithinScheduleWindowAllowsLateSyncOfACollectionMadeDuringHours() {
        // The real scenario this method exists for: an offline collection gathered during business
        // hours (collectedAt inside the window) only reaches the server well after closing time —
        // that late arrival must not retroactively reject a deposit that was legitimately made.
        UUID branchId = UUID.randomUUID();
        java.time.ZoneId zone = java.time.ZoneId.of("Africa/Douala");
        Agent agent = Agent.builder().id(agentId).branchId(branchId).build();
        Branch branch = Branch.builder().id(branchId).code("BR1").name("Branch 1")
                .openTime(java.time.LocalTime.of(8, 0)).closeTime(java.time.LocalTime.of(17, 0)).timezone(zone.getId()).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(branchRepository.findById(branchId)).thenReturn(Optional.of(branch));

        // Collected at 13:00 (well inside the window) but "now" — irrelevant to this call — could
        // be any time at all, including hours after close; only collectedAt is what's checked.
        java.time.Instant collectedAt = java.time.LocalDate.now(zone).atTime(13, 0).atZone(zone).toInstant();
        agentDirectoryService.requireWithinScheduleWindow(agentId, collectedAt);
    }

    @Test
    void requireDayNotEndedRejectsWhenTodaysBusinessDateAlreadyEnded() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        Agent agent = Agent.builder().id(agentId).dayEndedBusinessDate(today).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentDirectoryService.requireDayNotEnded(agentId, Instant.now()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void requireDayNotEndedAllowsCollectingOnANewBusinessDate() {
        java.time.LocalDate yesterday = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);
        Agent agent = Agent.builder().id(agentId).dayEndedBusinessDate(yesterday).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        agentDirectoryService.requireDayNotEnded(agentId, Instant.now());
    }

    @Test
    void requireDayNotEndedAllowsWhenDayHasNeverBeenEnded() {
        Agent agent = Agent.builder().id(agentId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        agentDirectoryService.requireDayNotEnded(agentId, Instant.now());
    }

    @Test
    void hasEndedDayTodayTrueWhenStampedForTodaysUtcDate() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        Agent agent = Agent.builder().id(agentId).dayEndedBusinessDate(today).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThat(agentDirectoryService.hasEndedDayToday(agentId)).isTrue();
    }

    @Test
    void hasEndedDayTodayFalseWhenStampedForAnEarlierDate() {
        java.time.LocalDate yesterday = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);
        Agent agent = Agent.builder().id(agentId).dayEndedBusinessDate(yesterday).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThat(agentDirectoryService.hasEndedDayToday(agentId)).isFalse();
    }

    @Test
    void hasEndedDayTodayFalseWhenNeverEnded() {
        Agent agent = Agent.builder().id(agentId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThat(agentDirectoryService.hasEndedDayToday(agentId)).isFalse();
    }

    @Test
    void hasEndedDayTodayUnknownAgentThrows404() {
        when(agentRepository.findById(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentDirectoryService.hasEndedDayToday(agentId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void markDayEndedStampsTodaysBusinessDateOnTheAgent() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        Agent agent = Agent.builder().id(agentId).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDirectoryService.markDayEnded(agentId, today);

        assertThat(agent.getDayEndedBusinessDate()).isEqualTo(today);
    }

    @Test
    void verifyTransactionPinRejectsReconciliationRequiredWithSpecificMessage() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).status(AgentStatus.RECONCILIATION_REQUIRED).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentDirectoryService.verifyTransactionPin(agentId, "1234"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403")
                .hasMessageContaining("no longer authorized");
    }

    @Test
    void verifyTransactionPinAllowsResetAuthorizedAgent() {
        Agent agent = Agent.builder().id(agentId).pinHash("hashed").pinMustChange(false).status(AgentStatus.RESET_AUTHORIZED).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(passwordEncoder.matches("1234", "hashed")).thenReturn(true);

        agentDirectoryService.verifyTransactionPin(agentId, "1234");
    }

    @Test
    void flipToReconciliationRequiredChangesStatus() {
        Agent agent = Agent.builder().id(agentId).status(AgentStatus.ACTIVE).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDirectoryService.flipToReconciliationRequired(agentId);

        assertThat(agent.getStatus()).isEqualTo(AgentStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    void flipToReconciliationRequiredNoOpsForSuspendedAgent() {
        Agent agent = Agent.builder().id(agentId).status(AgentStatus.SUSPENDED).build();
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        agentDirectoryService.flipToReconciliationRequired(agentId);

        assertThat(agent.getStatus()).isEqualTo(AgentStatus.SUSPENDED);
        verify(agentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void requireOnlineFirstCollectionCompletedForOfflineRejectsWhenBindingStillOwesOne() {
        AgentInstallationBinding binding = AgentInstallationBinding.builder().agentId(agentId).installationId("inst-1").onlineFirstCollectionCompletedAt(null).build();
        when(installationBindingService.findCurrent(agentId)).thenReturn(Optional.of(binding));

        assertThatThrownBy(() -> agentDirectoryService.requireOnlineFirstCollectionCompletedForOffline(agentId, CollectionOrigin.OFFLINE_SYNC))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void requireOnlineFirstCollectionCompletedForOfflineAllowsWhenAlreadyCompleted() {
        AgentInstallationBinding binding = AgentInstallationBinding.builder().agentId(agentId).installationId("inst-1").onlineFirstCollectionCompletedAt(Instant.now()).build();
        when(installationBindingService.findCurrent(agentId)).thenReturn(Optional.of(binding));

        agentDirectoryService.requireOnlineFirstCollectionCompletedForOffline(agentId, CollectionOrigin.OFFLINE_SYNC);
    }

    @Test
    void requireOnlineFirstCollectionCompletedForOfflineIsNoOpForOnlineOrigin() {
        agentDirectoryService.requireOnlineFirstCollectionCompletedForOffline(agentId, CollectionOrigin.ONLINE);

        verify(installationBindingService, org.mockito.Mockito.never()).findCurrent(any());
    }

    @Test
    void completeOnlineFirstCollectionIfNeededFlipsResetAuthorizedAgentBackToActive() {
        AgentInstallationBinding binding = AgentInstallationBinding.builder().agentId(agentId).installationId("inst-1").onlineFirstCollectionCompletedAt(null).build();
        Agent agent = Agent.builder().id(agentId).status(AgentStatus.RESET_AUTHORIZED).build();
        when(installationBindingService.findCurrent(agentId)).thenReturn(Optional.of(binding));
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agentDirectoryService.completeOnlineFirstCollectionIfNeeded(agentId, CollectionOrigin.ONLINE);

        verify(installationBindingService).markOnlineFirstCollectionCompleted(binding);
        assertThat(agent.getStatus()).isEqualTo(AgentStatus.ACTIVE);
    }

    @Test
    void completeOnlineFirstCollectionIfNeededIsNoOpForOfflineOrigin() {
        agentDirectoryService.completeOnlineFirstCollectionIfNeeded(agentId, CollectionOrigin.OFFLINE_SYNC);

        verify(installationBindingService, org.mockito.Mockito.never()).findCurrent(any());
    }

    @Test
    void completeOnlineFirstCollectionIfNeededIsNoOpWhenAlreadyCompleted() {
        AgentInstallationBinding binding = AgentInstallationBinding.builder().agentId(agentId).installationId("inst-1").onlineFirstCollectionCompletedAt(Instant.now()).build();
        when(installationBindingService.findCurrent(agentId)).thenReturn(Optional.of(binding));

        agentDirectoryService.completeOnlineFirstCollectionIfNeeded(agentId, CollectionOrigin.ONLINE);

        verify(installationBindingService, org.mockito.Mockito.never()).markOnlineFirstCollectionCompleted(any());
        verify(agentRepository, org.mockito.Mockito.never()).findById(agentId);
    }

    @Test
    void requireCurrentInstallationBindingReturnsInstallationIdAndSecret() {
        AgentInstallationBinding binding = AgentInstallationBinding.builder().agentId(agentId).installationId("inst-1").hmacSecretBase64("c2VjcmV0").build();
        when(installationBindingService.findCurrent(agentId)).thenReturn(Optional.of(binding));

        var result = agentDirectoryService.requireCurrentInstallationBinding(agentId);

        assertThat(result).isPresent();
        assertThat(result.get().installationId()).isEqualTo("inst-1");
        assertThat(result.get().hmacSecretBase64()).isEqualTo("c2VjcmV0");
    }
}
