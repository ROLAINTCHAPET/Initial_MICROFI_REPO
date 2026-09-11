package com.microfi.transactions.service;

import com.microfi.events.AgentMisconductReportedEvent;
import com.microfi.shared.dto.AgentMisconductReportRequest;
import com.microfi.shared.dto.AgentMisconductReportResponse;
import com.microfi.transactions.domain.AgentMisconductReport;
import com.microfi.transactions.repository.AgentMisconductReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A client reporting that their agent may have misbehaved — same "accept, let Back-Office triage" shape as TrackingService's SOS handling, just the other direction (client reporting on an agent). */
@Service
@RequiredArgsConstructor
@Transactional
public class AgentMisconductReportService {

    private final AgentMisconductReportRepository agentMisconductReportRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public AgentMisconductReportResponse report(UUID clientId, AgentMisconductReportRequest request) {
        AgentMisconductReport report = AgentMisconductReport.builder()
                .id(UUID.randomUUID())
                .agentId(request.getAgentId())
                .clientId(clientId)
                .reason(request.getReason())
                .reportedAt(Instant.now())
                .build();
        agentMisconductReportRepository.save(report);

        AgentMisconductReportResponse response = toResponse(report);
        applicationEventPublisher.publishEvent(new AgentMisconductReportedEvent(response));
        return response;
    }

    /** {@code agentIds == null} means unrestricted (ADMIN, global scope); a non-null list scopes to a branch's agents — see AgentDirectoryService#findAgentIdsByBranch. */
    public List<AgentMisconductReportResponse> list(List<UUID> agentIds, boolean unresolvedOnly) {
        List<AgentMisconductReport> reports;
        if (agentIds == null) {
            reports = unresolvedOnly ? agentMisconductReportRepository.findByReviewedAtIsNullOrderByReportedAtDesc()
                    : agentMisconductReportRepository.findAllByOrderByReportedAtDesc();
        } else {
            reports = unresolvedOnly ? agentMisconductReportRepository.findByAgentIdInAndReviewedAtIsNullOrderByReportedAtDesc(agentIds)
                    : agentMisconductReportRepository.findByAgentIdInOrderByReportedAtDesc(agentIds);
        }
        return reports.stream().map(this::toResponse).toList();
    }

    /** Returns the report's agentId so the controller can branch-scope before this is called — see AgentMisconductReportController. */
    public UUID findReportAgentId(UUID reportId) {
        return findReportOrThrow(reportId).getAgentId();
    }

    public AgentMisconductReportResponse markReviewed(UUID reportId, UUID reviewedByAdminId) {
        AgentMisconductReport report = findReportOrThrow(reportId);
        if (report.getReviewedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report already reviewed");
        }
        report.setReviewedBy(reviewedByAdminId);
        report.setReviewedAt(Instant.now());
        agentMisconductReportRepository.save(report);
        return toResponse(report);
    }

    private AgentMisconductReport findReportOrThrow(UUID reportId) {
        return agentMisconductReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Misconduct report not found: " + reportId));
    }

    private AgentMisconductReportResponse toResponse(AgentMisconductReport report) {
        return AgentMisconductReportResponse.builder()
                .id(report.getId())
                .agentId(report.getAgentId())
                .clientId(report.getClientId())
                .reason(report.getReason())
                .reportedAt(report.getReportedAt())
                .status(report.getReviewedAt() == null ? "PENDING" : "REVIEWED")
                .reviewedBy(report.getReviewedBy())
                .reviewedAt(report.getReviewedAt())
                .build();
    }
}
