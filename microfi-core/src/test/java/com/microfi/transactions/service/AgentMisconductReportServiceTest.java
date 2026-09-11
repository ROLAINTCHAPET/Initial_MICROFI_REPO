package com.microfi.transactions.service;

import com.microfi.events.AgentMisconductReportedEvent;
import com.microfi.shared.dto.AgentMisconductReportRequest;
import com.microfi.shared.dto.AgentMisconductReportResponse;
import com.microfi.transactions.domain.AgentMisconductReport;
import com.microfi.transactions.repository.AgentMisconductReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
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

class AgentMisconductReportServiceTest {

    @Mock
    private AgentMisconductReportRepository agentMisconductReportRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private AgentMisconductReportService service;

    private final UUID agentId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AgentMisconductReportService(agentMisconductReportRepository, applicationEventPublisher);
        when(agentMisconductReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private AgentMisconductReportRequest request(String reason) {
        AgentMisconductReportRequest request = new AgentMisconductReportRequest();
        request.setAgentId(agentId);
        request.setReason(reason);
        return request;
    }

    @Test
    void reportPersistsAndPublishesAlertUnconditionally() {
        AgentMisconductReportResponse response = service.report(clientId, request("Demanded a bribe"));

        assertThat(response.getAgentId()).isEqualTo(agentId);
        assertThat(response.getClientId()).isEqualTo(clientId);
        assertThat(response.getReason()).isEqualTo("Demanded a bribe");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getReportedAt()).isNotNull();
        verify(applicationEventPublisher).publishEvent(new AgentMisconductReportedEvent(response));
    }

    @Test
    void listUnrestrictedReturnsEveryReportWhenAgentIdsIsNull() {
        AgentMisconductReport report = AgentMisconductReport.builder().id(UUID.randomUUID()).agentId(agentId).clientId(clientId)
                .reason("Rude").reportedAt(Instant.now()).build();
        when(agentMisconductReportRepository.findAllByOrderByReportedAtDesc()).thenReturn(List.of(report));

        List<AgentMisconductReportResponse> results = service.list(null, false);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAgentId()).isEqualTo(agentId);
    }

    @Test
    void listScopedToBranchFiltersByAgentIds() {
        when(agentMisconductReportRepository.findByAgentIdInOrderByReportedAtDesc(List.of(agentId))).thenReturn(List.of());

        service.list(List.of(agentId), false);

        verify(agentMisconductReportRepository).findByAgentIdInOrderByReportedAtDesc(List.of(agentId));
    }

    @Test
    void markReviewedSetsReviewedByAndAt() {
        UUID reportId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AgentMisconductReport report = AgentMisconductReport.builder().id(reportId).agentId(agentId).clientId(clientId)
                .reason("Rude").reportedAt(Instant.now()).build();
        when(agentMisconductReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        AgentMisconductReportResponse response = service.markReviewed(reportId, adminId);

        assertThat(response.getStatus()).isEqualTo("REVIEWED");
        assertThat(response.getReviewedBy()).isEqualTo(adminId);
        assertThat(response.getReviewedAt()).isNotNull();
    }

    @Test
    void markReviewedAlreadyReviewedThrowsConflict() {
        UUID reportId = UUID.randomUUID();
        AgentMisconductReport report = AgentMisconductReport.builder().id(reportId).agentId(agentId).clientId(clientId)
                .reason("Rude").reportedAt(Instant.now()).reviewedBy(UUID.randomUUID()).reviewedAt(Instant.now()).build();
        when(agentMisconductReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.markReviewed(reportId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void findReportAgentIdUnknownReportThrows404() {
        UUID reportId = UUID.randomUUID();
        when(agentMisconductReportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findReportAgentId(reportId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
