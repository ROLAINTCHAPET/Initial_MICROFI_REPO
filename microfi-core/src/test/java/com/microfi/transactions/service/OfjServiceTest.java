package com.microfi.transactions.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.cbsclient.CbsClientService;
import com.microfi.savings.service.ActivationCashLine;
import com.microfi.savings.service.ActivationDirectoryService;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.DenominationLineDto;
import com.microfi.shared.dto.ExportRequest;
import com.microfi.shared.dto.MiddlewareExportAck;
import com.microfi.shared.dto.MiddlewareTransactionPostResult;
import com.microfi.shared.dto.OfjAgentLineResponse;
import com.microfi.shared.dto.OfjPendingLineResponse;
import com.microfi.shared.dto.OfjSummaryResponse;
import com.microfi.shared.dto.ReconcileRequest;
import com.microfi.shared.dto.VarianceDebtResponse;
import com.microfi.shared.dto.VarianceRequest;
import com.microfi.transactions.domain.Collection;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Mock
    private ClientDirectoryService clientDirectoryService;

    private OfjService ofjService;

    private final UUID branchId = UUID.randomUUID();
    private final UUID agentId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ofjService = new OfjService(ofjSessionRepository, ofjAgentLineRepository, ofjPhysicalDenomRepository,
                varianceDebtRepository, exportBatchRepository, collectionRepository, cbsClientService, agentDirectoryService,
                activationDirectoryService, clientDirectoryService);
        when(ofjSessionRepository.save(any(OfjSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(ofjAgentLineRepository.save(any(OfjAgentLine.class))).thenAnswer(inv -> inv.getArgument(0));
        when(varianceDebtRepository.save(any(VarianceDebt.class))).thenAnswer(inv -> inv.getArgument(0));
        // Default: branch is past its closing time, so existing reconcile tests aren't affected
        // by the new EOD gate — tests exercising that gate specifically override this.
        when(agentDirectoryService.isBranchPastCloseTime(branchId)).thenReturn(true);
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
    void reconcileRejectsBeforeBranchClosingTime() {
        when(agentDirectoryService.isBranchPastCloseTime(branchId)).thenReturn(false);

        assertThatThrownBy(() -> ofjService.reconcile(branchId, reconcileRequest(5000, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        org.mockito.Mockito.verifyNoInteractions(ofjSessionRepository);
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

    /**
     * Regression test for a bug caught by live testing: an agent who collected more cash after
     * their branch's session happened to auto-close (every agent known at the time balanced) had
     * no way to be reconciled again — the cashier's "pending" queue and the reconcile call itself
     * both silently refused to acknowledge the new cash. Reopening is safe as long as nothing has
     * been exported to the CBS yet (see {@link #reconcileRejectsWhenSessionAlreadyExported}).
     */
    @Test
    void reconcileReopensClosedSessionWhenNotYetExported() {
        OfjSession closed = openSession();
        closed.setStatus(OfjSessionStatus.CLOSED);
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));
        when(exportBatchRepository.findByOfjId(closed.getId())).thenReturn(Optional.empty());
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(closed.getId(), agentId)).thenReturn(Optional.empty());
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));

        OfjAgentLineResponse response = ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        assertThat(response.getPhysicalTotalXaf()).isEqualTo(5000);
        ArgumentCaptor<OfjSession> captor = ArgumentCaptor.forClass(OfjSession.class);
        verify(ofjSessionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().stream().anyMatch(s -> s.getStatus() == OfjSessionStatus.OPEN)).isTrue();
    }

    @Test
    void reconcileRejectsWhenSessionAlreadyExported() {
        OfjSession closed = openSession();
        closed.setStatus(OfjSessionStatus.CLOSED);
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));
        when(exportBatchRepository.findByOfjId(closed.getId())).thenReturn(Optional.of(
                com.microfi.transactions.domain.ExportBatch.builder().id(UUID.randomUUID()).ofjId(closed.getId())
                        .fileUri("export/x.csv").format("CSV").build()));

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
     * The whole point of auto-closing as soon as everyone balances: the client should see their
     * balance update immediately, not whenever someone remembers to click a separate Export
     * button — so closing must also post the day's cash to the CBS right away.
     */
    @Test
    void sessionCloseAutomaticallyExportsToTheCbs() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(5000L);
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        OfjAgentLine resolvedLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).deltaXaf(0).build();
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(resolvedLine));
        when(exportBatchRepository.findByOfjId(session.getId())).thenReturn(Optional.empty());
        when(collectionRepository.findByAgentIdInAndCollectedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(cbsClientService.submitDailyExport(any(), anyString(), anyString()))
                .thenReturn(Mono.just(MiddlewareExportAck.builder().acknowledged(true).ackReference("EXPACK-AUTO").build()));
        when(exportBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        verify(cbsClientService).submitDailyExport(eq(branchId), anyString(), anyString());
        ArgumentCaptor<com.microfi.transactions.domain.ExportBatch> captor = ArgumentCaptor.forClass(com.microfi.transactions.domain.ExportBatch.class);
        verify(exportBatchRepository).save(captor.capture());
        assertThat(captor.getValue().getAckStatus()).isEqualTo("ACKNOWLEDGED:EXPACK-AUTO");
    }

    /** A CBS/middleware hiccup during auto-export must never undo a reconciliation that already succeeded. */
    @Test
    void sessionCloseSucceedsEvenWhenAutomaticExportFails() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(5000L);
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        OfjAgentLine resolvedLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).deltaXaf(0).build();
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(resolvedLine));
        when(exportBatchRepository.findByOfjId(session.getId())).thenReturn(Optional.empty());
        when(collectionRepository.findByAgentIdInAndCollectedAtBetween(any(), any(), any()))
                .thenThrow(new RuntimeException("CBS unreachable"));

        OfjAgentLineResponse response = ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        assertThat(response.isResolved()).isTrue();
        org.mockito.Mockito.verify(exportBatchRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void autoExportSkipsWhenSessionAlreadyExported() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(5000L);
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        OfjAgentLine resolvedLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).deltaXaf(0).build();
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(resolvedLine));
        when(exportBatchRepository.findByOfjId(session.getId())).thenReturn(Optional.of(
                com.microfi.transactions.domain.ExportBatch.builder().id(UUID.randomUUID()).ofjId(session.getId())
                        .fileUri("export/x.csv").format("CSV").build()));

        ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        verify(cbsClientService, org.mockito.Mockito.never()).submitDailyExport(any(), anyString(), anyString());
    }

    /**
     * Design handoff §6.1: even when every reconciled line looks resolved, close must stay
     * blocked while any active agent still has collections queued locally (self-reported via
     * PATCH /agents/{id}/sync-status) — that cash never reached digital_total_xaf.
     */
    @Test
    void sessionStaysOpenWhileAnyAgentHasUnsyncedCollectionsQueuedLocally() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(ofjAgentLineRepository.findByOfjIdAndAgentId(session.getId(), agentId)).thenReturn(Optional.empty());
        when(collectionRepository.sumAmountByAgentAndWindow(any(), any(), any())).thenReturn(5000L);
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(agentDirectoryService.hasPendingUnsyncedCollections(List.of(agentId))).thenReturn(true);
        OfjAgentLine resolvedLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).deltaXaf(0).build();
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(resolvedLine));

        ofjService.reconcile(branchId, reconcileRequest(5000, 1));

        org.mockito.Mockito.verify(ofjSessionRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.argThat(s -> s.getStatus() == OfjSessionStatus.CLOSED));
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

    @Test
    void exportRejectsWhenSessionAlreadyExported() {
        OfjSession closed = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today).status(OfjSessionStatus.CLOSED).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));
        when(exportBatchRepository.findByOfjId(closed.getId())).thenReturn(Optional.of(
                com.microfi.transactions.domain.ExportBatch.builder().id(UUID.randomUUID()).ofjId(closed.getId())
                        .fileUri("export/x.csv").format("CSV").build()));

        assertThatThrownBy(() -> ofjService.exportDaily(branchId, new ExportRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        verify(cbsClientService, org.mockito.Mockito.never()).submitDailyExport(any(), anyString(), anyString());
    }

    /**
     * Regression test: the mock CBS used to return balances/history with no connection to what
     * agents actually collected. Export must post the branch's collections to the CBS ledger
     * (resolving each client's CBS ref) before acknowledging the export, so getBalance/getHistory
     * downstream genuinely reflect the day's activity.
     */
    @Test
    void exportPostsBranchCollectionsToCbsLedgerBeforeSubmission() {
        OfjSession closed = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today).status(OfjSessionStatus.CLOSED).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));
        when(cbsClientService.submitDailyExport(any(), anyString(), anyString()))
                .thenReturn(Mono.just(MiddlewareExportAck.builder().acknowledged(true).ackReference("EXPACK-1").build()));
        when(exportBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID clientId = UUID.randomUUID();
        Collection collection = Collection.builder().id(UUID.randomUUID()).agentId(agentId).clientId(clientId)
                .amountXaf(2000L).collectedAt(Instant.now()).build();
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(collectionRepository.findByAgentIdInAndCollectedAtBetween(any(), any(), any())).thenReturn(List.of(collection));
        when(clientDirectoryService.findCbsRef(clientId)).thenReturn("CBS-XYZ");
        when(cbsClientService.postTransactions(any(), anyString()))
                .thenReturn(Mono.just(MiddlewareTransactionPostResult.builder().success(true).postedReferences(List.of("CBSTX-1")).build()));

        ofjService.exportDaily(branchId, new ExportRequest());

        verify(cbsClientService, times(1)).postTransactions(any(), anyString());
    }

    /** A branch with no collections that day must not call the middleware with an empty/@NotEmpty-violating list. */
    @Test
    void exportSkipsLedgerPostingWhenNoCollections() {
        OfjSession closed = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today).status(OfjSessionStatus.CLOSED).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));
        when(cbsClientService.submitDailyExport(any(), anyString(), anyString()))
                .thenReturn(Mono.just(MiddlewareExportAck.builder().acknowledged(true).ackReference("EXPACK-1").build()));
        when(exportBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(collectionRepository.findByAgentIdInAndCollectedAtBetween(any(), any(), any())).thenReturn(List.of());

        ofjService.exportDaily(branchId, new ExportRequest());

        verify(cbsClientService, times(0)).postTransactions(any(), anyString());
    }

    /**
     * Regression test: UC-19 activation fees are real cash the client hands the agent in person,
     * exactly like a Collection (see feedback_microfi_activation_two_party_gate memory) — they
     * must also reach the CBS ledger on export, not just Collection rows, or an activated client's
     * fee payment would never show up in their own balance/history.
     */
    @Test
    void exportPostsActivationPaymentsToCbsLedgerEvenWithNoCollections() {
        OfjSession closed = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today).status(OfjSessionStatus.CLOSED).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(closed));
        when(cbsClientService.submitDailyExport(any(), anyString(), anyString()))
                .thenReturn(Mono.just(MiddlewareExportAck.builder().acknowledged(true).ackReference("EXPACK-1").build()));
        when(exportBatchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID clientId = UUID.randomUUID();
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(collectionRepository.findByAgentIdInAndCollectedAtBetween(any(), any(), any())).thenReturn(List.of());
        when(activationDirectoryService.findByAgentIdsAndWindow(any(), any(), any()))
                .thenReturn(List.of(new ActivationCashLine(UUID.randomUUID(), clientId, 1000L, Instant.now())));
        when(clientDirectoryService.findCbsRef(clientId)).thenReturn("CBS-ACT-1");
        when(cbsClientService.postTransactions(any(), anyString()))
                .thenReturn(Mono.just(MiddlewareTransactionPostResult.builder().success(true).postedReferences(List.of("CBSTX-1")).build()));

        ofjService.exportDaily(branchId, new ExportRequest());

        ArgumentCaptor<List<com.microfi.shared.dto.MiddlewareCollectionLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(cbsClientService, times(1)).postTransactions(captor.capture(), anyString());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getMemberId()).isEqualTo("CBS-ACT-1");
        assertThat(captor.getValue().get(0).getAmountXaf()).isEqualTo(1000L);
    }

    @Test
    void getSummaryWithTodayDateBehavesLikeNoDateOverload() {
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.empty());
        when(ofjAgentLineRepository.findByOfjId(any())).thenReturn(List.of());

        OfjSummaryResponse summary = ofjService.getSummary(branchId, today);

        assertThat(summary.getBranchId()).isEqualTo(branchId);
        verify(ofjSessionRepository).save(any(OfjSession.class));
    }

    @Test
    void getSummaryWithPastDateIsReadOnlyAndFoundReturnsIt() {
        LocalDate yesterday = today.minusDays(1);
        OfjSession pastSession = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(yesterday).status(OfjSessionStatus.CLOSED).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, yesterday)).thenReturn(Optional.of(pastSession));
        when(ofjAgentLineRepository.findByOfjId(pastSession.getId())).thenReturn(List.of());

        OfjSummaryResponse summary = ofjService.getSummary(branchId, yesterday);

        assertThat(summary.getBusinessDate()).isEqualTo(yesterday);
        assertThat(summary.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void getSummaryWithPastDateNotFoundThrows404() {
        LocalDate yesterday = today.minusDays(1);
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, yesterday)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ofjService.getSummary(branchId, yesterday))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void listHistoryReturnsSessionsMostRecentFirst() {
        OfjSession s1 = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today).status(OfjSessionStatus.CLOSED).build();
        OfjSession s2 = OfjSession.builder().id(UUID.randomUUID()).branchId(branchId).businessDate(today.minusDays(1)).status(OfjSessionStatus.CLOSED).build();
        when(ofjSessionRepository.findByBranchIdOrderByBusinessDateDesc(branchId)).thenReturn(List.of(s1, s2));
        when(ofjAgentLineRepository.findByOfjId(any())).thenReturn(List.of());

        List<OfjSummaryResponse> history = ofjService.listHistory(branchId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getBusinessDate()).isEqualTo(today);
    }

    @Test
    void listVarianceDebtsForAgentFiltersOpenOnlyWhenRequested() {
        VarianceDebt debt = VarianceDebt.builder().id(UUID.randomUUID()).agentId(agentId).ofjAgentLineId(UUID.randomUUID()).amountXaf(500L).build();
        when(varianceDebtRepository.findByAgentIdAndStatusOrderByCreatedAtDesc(agentId, com.microfi.transactions.domain.VarianceDebtStatus.OPEN))
                .thenReturn(List.of(debt));

        List<VarianceDebtResponse> result = ofjService.listVarianceDebtsForAgent(agentId, true);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAgentId()).isEqualTo(agentId);
    }

    @Test
    void listVarianceDebtsForBranchReturnsEmptyWhenNoAgents() {
        when(agentDirectoryService.findAgentIdsByBranch(branchId)).thenReturn(List.of());

        List<VarianceDebtResponse> result = ofjService.listVarianceDebtsForBranch(branchId, false);

        assertThat(result).isEmpty();
    }

    /**
     * The whole point of {@code listPendingAgents}: an agent who's collected cash today but never
     * been reconciled has no {@code OfjAgentLine} yet — so {@code summary.agentLines} alone can't
     * surface them as "awaiting reconciliation." This is the cashier's actual queue source.
     */
    @Test
    void listPendingAgentsIncludesActiveAgentWithCollectionsAndNoExistingLine() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of());
        when(collectionRepository.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(4000L);
        when(activationDirectoryService.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(500L);

        List<OfjPendingLineResponse> pending = ofjService.listPendingAgents(branchId);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getAgentId()).isEqualTo(agentId);
        assertThat(pending.get(0).getCollectionsTotalXaf()).isEqualTo(4000);
        assertThat(pending.get(0).getActivationsTotalXaf()).isEqualTo(500);
        assertThat(pending.get(0).getDigitalTotalXaf()).isEqualTo(4500);
    }

    @Test
    void listPendingAgentsExcludesAgentsWhoseLineIsStillCurrent() {
        OfjSession session = openSession();
        OfjAgentLine existingLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).digitalTotalXaf(4500L).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(existingLine));
        when(collectionRepository.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(4000L);
        when(activationDirectoryService.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(500L);

        List<OfjPendingLineResponse> pending = ofjService.listPendingAgents(branchId);

        assertThat(pending).isEmpty();
    }

    /**
     * Regression test for a bug caught by live testing: an agent whose collections started, got
     * reconciled once (recording an OfjAgentLine), and then collected MORE afterward was silently
     * excluded from the pending queue forever, since the old logic only checked "does a line
     * exist" rather than "is the line still current" — the cashier never saw the new cash at all.
     */
    @Test
    void listPendingAgentsIncludesAgentsWhoseLineIsStale() {
        OfjSession session = openSession();
        OfjAgentLine staleLine = OfjAgentLine.builder().id(UUID.randomUUID()).ofjId(session.getId()).agentId(agentId).digitalTotalXaf(4500L).build();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of(staleLine));
        // Agent has collected more since the line was recorded.
        when(collectionRepository.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(8000L);
        when(activationDirectoryService.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(500L);

        List<OfjPendingLineResponse> pending = ofjService.listPendingAgents(branchId);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getAgentId()).isEqualTo(agentId);
        assertThat(pending.get(0).getDigitalTotalXaf()).isEqualTo(8500);
    }

    @Test
    void listPendingAgentsExcludesAgentsWithNoCollectionsToday() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of(agentId));
        when(ofjAgentLineRepository.findByOfjId(session.getId())).thenReturn(List.of());
        when(collectionRepository.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(0L);
        when(activationDirectoryService.sumAmountByAgentAndWindow(eq(agentId), any(), any())).thenReturn(0L);

        List<OfjPendingLineResponse> pending = ofjService.listPendingAgents(branchId);

        assertThat(pending).isEmpty();
    }

    @Test
    void listPendingAgentsReturnsEmptyWhenBranchHasNoActiveAgents() {
        OfjSession session = openSession();
        when(ofjSessionRepository.findByBranchIdAndBusinessDate(branchId, today)).thenReturn(Optional.of(session));
        when(agentDirectoryService.findActiveAgentIdsByBranch(branchId)).thenReturn(List.of());

        List<OfjPendingLineResponse> pending = ofjService.listPendingAgents(branchId);

        assertThat(pending).isEmpty();
    }
}
