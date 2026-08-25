package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.shared.dto.EscrowResponse;
import com.microfi.transactions.domain.EscrowAccount;
import com.microfi.transactions.repository.CeilingOverrideRepository;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.EscrowAccountRepository;
import com.microfi.transactions.repository.EscrowLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EscrowServiceTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;
    @Mock
    private EscrowLedgerRepository escrowLedgerRepository;
    @Mock
    private CeilingOverrideRepository ceilingOverrideRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private ActivationDirectoryService activationDirectoryService;
    @Mock
    private AgentDirectoryService agentDirectoryService;

    private EscrowService escrowService;

    private final UUID agentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        escrowService = new EscrowService(escrowAccountRepository, escrowLedgerRepository, ceilingOverrideRepository,
                collectionRepository, activationDirectoryService, agentDirectoryService);
        when(escrowLedgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(escrowAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ceilingOverrideRepository.findFirstByAgentIdAndValidUntilAfterOrderByValidUntilDesc(any(), any())).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(0L);
        when(activationDirectoryService.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(0L);
        // Default branch policy is 100% (1:1) unless a test overrides it.
        when(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).thenReturn(100);
    }

    private EscrowAccount account(long balance, long ceiling) {
        return EscrowAccount.builder().id(UUID.randomUUID()).agentId(agentId).balanceXaf(balance).ceilingXaf(ceiling).build();
    }

    @Test
    void topUpAt100PctRaisesCeiling1to1WithDeposit() {
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.of(account(0, 0)));

        EscrowResponse response = escrowService.topUp(agentId, 50_000, "REF-1", UUID.randomUUID(), "proof/path.jpg");

        assertThat(response.getBalanceXaf()).isEqualTo(50_000);
        assertThat(response.getBaseCeilingXaf()).isEqualTo(50_000);
    }

    @Test
    void topUpAt150PctGrantsCeilingAboveDeposit() {
        when(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).thenReturn(150);
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.of(account(0, 0)));

        EscrowResponse response = escrowService.topUp(agentId, 50_000, "REF-1", UUID.randomUUID(), "proof/path.jpg");

        assertThat(response.getBalanceXaf()).isEqualTo(50_000);
        assertThat(response.getBaseCeilingXaf()).isEqualTo(75_000);
    }

    @Test
    void topUpAt50PctGrantsCeilingBelowDeposit() {
        when(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).thenReturn(50);
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.of(account(0, 0)));

        EscrowResponse response = escrowService.topUp(agentId, 50_000, "REF-1", UUID.randomUUID(), "proof/path.jpg");

        assertThat(response.getBalanceXaf()).isEqualTo(50_000);
        assertThat(response.getBaseCeilingXaf()).isEqualTo(25_000);
    }

    @Test
    void topUpActivatesPendingCeilingAgentOnceCeilingBecomesPositive() {
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.of(account(0, 0)));

        escrowService.topUp(agentId, 10_000, "REF-1", UUID.randomUUID(), "proof/path.jpg");

        verify(agentDirectoryService).activateIfPendingCeiling(agentId);
    }

    @Test
    void topUpAtZeroPctDoesNotActivateSinceCeilingStaysZero() {
        when(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).thenReturn(0);
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.of(account(0, 0)));

        escrowService.topUp(agentId, 10_000, "REF-1", UUID.randomUUID(), "proof/path.jpg");

        verify(agentDirectoryService, never()).activateIfPendingCeiling(any());
    }

    @Test
    void topUpRecordsLedgerEntryWithFullDepositAmountRegardlessOfCeilingPct() {
        when(agentDirectoryService.effectiveCeilingPctForAgent(agentId)).thenReturn(150);
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.of(account(0, 0)));

        escrowService.topUp(agentId, 50_000, "REF-1", UUID.randomUUID(), "proof/path.jpg");

        ArgumentCaptor<com.microfi.transactions.domain.EscrowLedger> captor = ArgumentCaptor.forClass(com.microfi.transactions.domain.EscrowLedger.class);
        verify(escrowLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getDeltaXaf()).isEqualTo(50_000);
        assertThat(captor.getValue().getReason()).isEqualTo("TOP_UP");
    }

    @Test
    void topUpUnknownAgentThrows404() {
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> escrowService.topUp(agentId, 10_000, "REF-1", UUID.randomUUID(), "proof/path.jpg"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void applyCeilingOverrideNotAffectedByBranchCeilingPct() {
        when(escrowAccountRepository.findByAgentId(agentId)).thenReturn(Optional.of(account(50_000, 50_000)));
        Instant validUntil = Instant.now().plusSeconds(3600);
        when(ceilingOverrideRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ceilingOverrideRepository.findFirstByAgentIdAndValidUntilAfterOrderByValidUntilDesc(any(), any()))
                .thenReturn(Optional.of(com.microfi.transactions.domain.CeilingOverride.builder()
                        .id(UUID.randomUUID()).agentId(agentId).tempCeilingXaf(1_000_000).reason("Emergency").validUntil(validUntil).build()));

        EscrowResponse response = escrowService.applyCeilingOverride(agentId, 1_000_000, "Emergency", validUntil);

        assertThat(response.getEffectiveCeilingXaf()).isEqualTo(1_000_000);
        assertThat(response.getBaseCeilingXaf()).isEqualTo(50_000);
        verify(agentDirectoryService, never()).effectiveCeilingPctForAgent(any());
    }
}
