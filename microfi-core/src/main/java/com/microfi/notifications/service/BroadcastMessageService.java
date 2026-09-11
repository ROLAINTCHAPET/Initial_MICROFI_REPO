package com.microfi.notifications.service;

import com.microfi.authentication.AdminUserDetails;
import com.microfi.authentication.domain.AdminRole;
import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.notifications.domain.BroadcastAudience;
import com.microfi.notifications.domain.BroadcastMessage;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.repository.BroadcastMessageRepository;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.shared.dto.BroadcastMessageRequest;
import com.microfi.shared.dto.BroadcastMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ADMIN/BRANCH_MANAGER announcements to every client or every agent — generalizes {@link
 * com.microfi.notifications.domain.BranchNotice}/{@code NotificationService#notifyBranchScheduleChange}
 * (branch-only, agent-only) to also reach clients and to support a network-wide send. Same
 * delivery shape: persist one row the mobile app polls (see {@code AgentSelfController}'s
 * {@code /broadcasts} and {@code ClientSelfServiceController}'s {@code /broadcasts}), and SMS-blast
 * every resolved recipient phone, fire-and-forget, exactly like {@code notifyBranchScheduleChange}
 * — a slow/failing gateway must never block the sender's request. Email is a deliberately open
 * seam ({@link com.microfi.notifications.domain.NotificationChannel} already has room for it) —
 * not wired yet, pending mail configuration.
 */
@Service
@RequiredArgsConstructor
public class BroadcastMessageService {

    private static final int RECENT_LIMIT = 20;

    private final BroadcastMessageRepository broadcastMessageRepository;
    private final AgentDirectoryService agentDirectoryService;
    private final ClientDirectoryService clientDirectoryService;
    private final SmsGatewayFactory smsGatewayFactory;

    public BroadcastMessageResponse broadcast(AdminUserDetails caller, BroadcastMessageRequest request) {
        BroadcastAudience audience = parseAudience(request.getAudience());
        // BRANCH_MANAGER can only ever reach their own branch — silently forcing this (rather than
        // 403ing a mismatched payload) matches AdminAccess.requireBranchScope's spirit but there's
        // no separate "target" resource here to scope against, just this choice itself.
        UUID branchId = caller.getAdminUser().getRole() == AdminRole.ADMIN ? request.getBranchId() : caller.getAdminUser().getBranchId();

        List<String> phones = resolvePhones(audience, branchId);

        BroadcastMessage saved = BroadcastMessage.builder()
                .id(UUID.randomUUID())
                .senderAdminId(caller.getAdminUser().getId())
                .senderLabel(caller.getAdminUser().getLogin())
                .audience(audience)
                .branchId(branchId)
                .message(request.getMessage())
                .recipientCount(phones.size())
                .createdAt(Instant.now())
                .build();
        broadcastMessageRepository.save(saved);

        for (String phone : phones) {
            smsGatewayFactory.getActiveGateway().send(phone, request.getMessage())
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(result -> { }, error -> { });
        }

        return toResponse(saved, true);
    }

    /** A client's own poll — network-wide announcements plus their own branch's. */
    public List<BroadcastMessageResponse> listForClients(UUID branchId) {
        return broadcastMessageRepository.findRecentForAudienceAndBranch(BroadcastAudience.CLIENTS, branchId, PageRequest.of(0, RECENT_LIMIT))
                .stream().map(m -> toResponse(m, false)).toList();
    }

    /** An agent's own poll — network-wide announcements plus their own branch's. */
    public List<BroadcastMessageResponse> listForAgents(UUID branchId) {
        return broadcastMessageRepository.findRecentForAudienceAndBranch(BroadcastAudience.AGENTS, branchId, PageRequest.of(0, RECENT_LIMIT))
                .stream().map(m -> toResponse(m, false)).toList();
    }

    /** Back-Office send history — ADMIN sees everything, BRANCH_MANAGER only their own branch's sends. */
    public List<BroadcastMessageResponse> history(UUID scopedBranchId, boolean unrestricted) {
        List<BroadcastMessage> messages = unrestricted
                ? broadcastMessageRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_LIMIT))
                : broadcastMessageRepository.findByBranchIdOrderByCreatedAtDesc(scopedBranchId, PageRequest.of(0, RECENT_LIMIT));
        return messages.stream().map(m -> toResponse(m, true)).toList();
    }

    private List<String> resolvePhones(BroadcastAudience audience, UUID branchId) {
        if (audience == BroadcastAudience.AGENTS) {
            return branchId == null ? agentDirectoryService.findAllAgentPhones() : agentDirectoryService.findAgentPhonesByBranch(branchId);
        }
        return branchId == null ? clientDirectoryService.findAllClientPhones() : clientDirectoryService.findClientPhonesByBranch(branchId);
    }

    private BroadcastAudience parseAudience(String raw) {
        try {
            return BroadcastAudience.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "audience must be CLIENTS or AGENTS");
        }
    }

    private BroadcastMessageResponse toResponse(BroadcastMessage message, boolean includeRecipientCount) {
        return BroadcastMessageResponse.builder()
                .id(message.getId())
                .audience(message.getAudience().name())
                .branchId(message.getBranchId())
                .message(message.getMessage())
                .senderLabel(message.getSenderLabel())
                .createdAt(message.getCreatedAt())
                .recipientCount(includeRecipientCount ? message.getRecipientCount() : null)
                .build();
    }
}
