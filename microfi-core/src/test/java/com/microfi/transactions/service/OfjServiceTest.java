package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.shared.dto.DenominationLineDto;
import com.microfi.shared.dto.ExportRequest;
import com.microfi.shared.dto.MiddlewareExportAck;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.ReconcileRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.VarianceRequest;
import com.microfi.transactions.domain.OfjAgentLine;
import com.microfi.transactions.domain.OfjSession;
import com.microfi.transactions.domain.OfjSessionStatus;
import com.microfi.transactions.domain.VarianceDebt;
import com.microfi.transactions.repository.CollectionRepository;
import com.microfi.transactions.repository.ExportBatchRepository;
import com.microfi.transactions.repository.OfjAgentLineRepository;
import com.microfi.transactions.repository.OfjPhysicalDenomRepository;
import com.microfi.transactions.repository.OfjSessionRepository;
import com.microfi.transactions.repository.VarianceDebtRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class OfjServiceTest {

    @Mock
    private OfjSessionRepository ofjSessionRepository;
    @Mock
    private OfjAgentLineRepository ofjAgentLineRepository;
    @Mock
    private OfjPhysicalDenomRepository ofjPhysicalDenomRepository;
    @Mock
    private VarianceDebtRepository varianceDebtRepository;
    @Mock
    private ExportBatchRepository exportBatchRepository;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private CbsClientService cbsClientService;
    @Mock
    private AgentDirectoryService agentDirectoryService;
    @Mock
    private ActivationDirectoryService activationDirectoryService;

    private OfjService ofjService;

    private final UUID branchId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ofjService = new OfjService(ofjSessionRepository, ofjAgentLineRepository, ofjPhysicalDenomRepository,
                varianceDebtRepository, exportBatchRepository, collectionRepository, cbsClientService, agentDirectoryService,
                activationDirectoryService);
        when(ofjSessionRepository.save(any(OfjSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ofjAgentLineRepository.save(any(OfjAgentLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(varianceDebtRepository.save(any(VarianceDebt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OfjSession openSession() {
        return OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today).status(OfjSessionStatus.OPEN).build();
    }

    private ReconcileRequest reconcileRequest(long faceValue, int qty) {
        ReconcileRequest request = new ReconcileRequest();
        request.setAgentId(agentId);
        DenominationLineDto line = new DenominationLineDto();
        line.setFaceValueXaf(faceValue);
        line.setQuantity(qty);
        request.setPhysicalDenominationLines(List.of(line));
        return request;
    }

    @Test
    void summaryCreatesSessionOnFirstAccess() {
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.empty());
        when(ofjAgentLineRepository.findByOfjId(any())).thenReturn(List.of());

        OfjSummaryResponse summary = ofjService.getSummary(branchId);

        assertThat(summary.getStatus()).isEqualTo("OPEN");
        assertThat(summary.getAgentLines()).isEmpty();
    }

    @Test
    void reconcileComputesPositiveDelta() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(4000L);
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of());

        OfjAgentLineResponse response = ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        assertThat(response.getDigitalTotalXaf()).isEqualTo(4000);
        assertThat(response.getPhysicalTotalXaf()).isEqualTo(5000);
        assertThat(response.getDeltaXaf()).isEqualTo(1000);
        assertThat(response.isResolved()).isTrue();
    }

    @Test
    void reconcileSeparatesCollectionsFromActivationsInDigitalTotal() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(3000L);
        when(activationDirectoryService.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(1000L);
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of());

        OfjAgentLineResponse response = ofjService.reconcile(branchId, reconcileRequest(4000, 1));

        assertThat(response.getCollectionsTotalXaf()).isEqualTo(3000);
        assertThat(response.getActivationsTotalXaf()).isEqualTo(1000);
        assertThat(response.getDigitalTotalXaf()).isEqualTo(4000);
        assertThat(response.getDeltaXaf()).isEqualTo(0);
    }

    @Test
    void reconcileComputesNegativeDeltaAndLeavesUnresolved() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(5000L);
        when(varianceDebtRepository.findByOfjAgentLineId(any())).thenReturn(Optional.empty());

        OfjAgentLineResponse response = ofjService.reconcile(branchId, reconcileRequest(3000, 1));

        assertThat(response.getDeltaXaf()).isEqualTo(-2000);
        assertThat(response.isResolved()).isFalse();
    }

    @Test
    void reconcileRejectsWhenSessionAlreadyClosed() {
        OfjSession closed = openSession();
        closed.setStatus(OfjSessionStatus.CLOSED);
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> ofjService.reconcile(branchId, reconcileRequest(5000, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void sessionAutoClosesWhenTheSoleBranchAgentIsResolved() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(5000L);
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        OfjAgentLine resolvedLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).deltaXaf(0).build();
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(resolvedLine));

        ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        ArgumentCaptor<OfjSession> captor = ArgumentCaptor.forClass(OfjSession.class);
        org.mockito.Mockito.verify(ofjSessionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(s -> s.getStatus() == OfjSessionStatus.CLOSED)).isTrue();
    }

    /**
     * Regression test for a bug caught by live smoke testing: the session used to close as soon
     * as every agent line that *existed so far* was resolved, even if other active agents in the
     * branch hadn't reconciled at all yet. It must stay OPEN until every active agent in the
     * branch — not just the ones who happened to go first — has a resolved line.
     */
    @Test
    void sessionStaysOpenWhileOtherBranchAgentsHaveNotReconciledYet() {
        OfjSession session = openSession();
        UUID otherAgentId = UUID.randomUUID();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(5000L);
        // Branch has two active agents; only the first has reconciled (exact match, resolved).
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId, otherAgentId));
        OfjAgentLine resolvedLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).deltaXaf(0).build();
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(resolvedLine));

        ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        // Correct behavior here is that the session is never even saved with CLOSED status —
        // it may not be saved at all, since nothing changed about the session itself.
        org.mockito.Mockito.verify(ofjSessionRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.argThat(s -> s.getStatus() == OfjSessionStatus.CLOSED));
    }

    @Test
    void recordVarianceRejectsNonNegativeDelta() {
        OfjAgentLine line = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(UUID.randomUUID()).agentId(agentId).deltaXaf(500).build();
        when(ofjAgentLineRepository.findById(line.getId())).thenReturn(Optional.of(line));
        VarianceRequest request = new VarianceRequest();
        request.setOfjAgentLineId(line.getId());

        assertThatThrownBy(() -> ofjService.recordVariance(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BR-Var-01");
    }

    @Test
    void recordVarianceRejectsDuplicateDebt() {
        OfjAgentLine line = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(UUID.randomUUID()).agentId(agentId).deltaXaf(-500).build();
        when(ofjAgentLineRepository.findById(line.getId())).thenReturn(Optional.of(line));
        when(varianceDebtRepository.findByOfjAgentLineId(line.getId())).thenReturn(Optional.of(VarianceDebt.builder().build()));
        VarianceRequest request = new VarianceRequest();
        request.setOfjAgentLineId(line.getId());

        assertThatThrownBy(() -> ofjService.recordVariance(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void recordVarianceSucceedsForShortage() {
        UUID ofjId = UUID.randomUUID();
        OfjAgentLine line = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(ofjId).agentId(agentId).deltaXaf(-1500).build();
        when(ofjAgentLineRepository.findById(line.getId())).thenReturn(Optional.of(line));
        when(varianceDebtRepository.findByOfjAgentLineId(line.getId())).thenReturn(Optional.empty());
        when(ofjSessionRepository.findById(ofjId)).thenReturn(Optional.of(
                OfjSession.builder().id(ofjId).branchId(branchId).businessDate(today).status(OfjSessionStatus.OPEN).build()));
        when(ofjAgentLineRepository.findByOfjId(ofjId)).thenReturn(List.of(line));

        VarianceRequest request = new VarianceRequest();
        request.setOfjAgentLineId(line.getId());

        VarianceDebtResponse response = ofjService.recordVariance(request);

        assertThat(response.getAmountXaf()).isEqualTo(1500);
        assertThat(response.getAgentId()).isEqualTo(agentId);
    }

    @Test
    void exportRejectsWithoutClosedSession() {
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ofjService.exportDaily(branchId, new ExportRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("BR-Export-01");
    }

    @Test
    void exportSucceedsForClosedSession() {
        OfjSession closed = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today).status(OfjSessionStatus.CLOSED).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));
        when(cbsClientService.submitDailyExport(any(), anyString(), anyString()))
                .thenReturn(Mono.just(MiddlewareExportAck.builder().acknowledged(true).ackReference("EXPACK-1").build()));
        when(exportBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = ofjService.exportDaily(branchId, new ExportRequest());

        assertThat(response.getAckStatus()).isEqualTo("ACKNOWLEDGED:EXPACK-1");
        assertThat(response.getFormat()).isEqualTo("CSV");
    }
}
