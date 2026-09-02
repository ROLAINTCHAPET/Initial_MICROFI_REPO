package com.microfi.notifications.service;

import com.microfi.authentication.service.AgentDirectoryService;
import com.microfi.authentication.service.AgentDirectoryService.AgentReceiptInfo;
import com.microfi.notifications.domain.BranchNotice;
import com.microfi.notifications.domain.NotificationChannel;
import com.microfi.notifications.domain.NotificationLog;
import com.microfi.notifications.domain.NotificationStatus;
import com.microfi.notifications.gateway.SmsGatewayFactory;
import com.microfi.notifications.gateway.SmsSendResult;
import com.microfi.notifications.repository.BranchNoticeRepository;
import com.microfi.notifications.repository.NotificationLogRepository;
import com.microfi.savings.service.ClientDirectoryService;
import com.microfi.savings.service.ClientDirectoryService.ClientReceiptInfo;
import com.microfi.shared.dto.BranchNoticeResponse;
import com.microfi.shared.dto.NotificationLogResponse;
import com.microfi.shared.dto.NotifyCollectionRequest;
import com.microfi.shared.dto.ReceiptDataResponse;
import com.microfi.transactions.service.CollectionDirectoryService;
import com.microfi.transactions.service.CollectionDirectoryService.DenominationLineView;
import com.microfi.transactions.service.CollectionSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * UC-09 — Post-Validation Multi-Channel Notification. Composes the Flash SMS (amount, date,
 * agent reference) and sends it via whichever carrier {@link SmsGatewayFactory} resolves,
 * recording the outcome either way (BR-Notif-01-adjacent audit trail) — a gateway failure never
 * throws, since per UC-09's A1 alternative scenario the collection itself stays valid regardless
 * of whether the SMS could be delivered.
 * <p>
 * Also composes {@code receiptText} for the mobile app's Bluetooth thermal printer (the other
 * UC-09 channel) — printing itself is entirely a device-local action this backend can't perform,
 * but BR-Notif-01's mandatory legal mentions (MFI name, amount, date, agent ID) need one source of
 * truth so every agent's app doesn't reimplement that formatting independently.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter MESSAGE_DATE_FORMAT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.FRENCH).withZone(ZoneOffset.UTC);

    private final NotificationLogRepository notificationLogRepository;
    private final BranchNoticeRepository branchNoticeRepository;
    private final CollectionDirectoryService collectionDirectoryService;
    private final ClientDirectoryService clientDirectoryService;
    private final AgentDirectoryService agentDirectoryService;
    private final SmsGatewayFactory smsGatewayFactory;
    private final MfiSettingsService mfiSettingsService;

    public Mono<NotificationLogResponse> notifyCollection(UUID collectionId, UUID callerAgentId, NotifyCollectionRequest request) {
        boolean french = "fr".equals(request.getLocale());
        return Mono.fromCallable(() -> prepare(collectionId, callerAgentId, french))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(prepared -> smsGatewayFactory.getActiveGateway().send(prepared.phone(), prepared.message())
                        .flatMap(result -> Mono.fromCallable(() -> persist(prepared, result, request.isPrintedReceipt()))
                                .subscribeOn(Schedulers.boundedElastic())));
    }

    /**
     * UC-15 follow-on: agents don't watch the Back-Office, so a same-day opening/closing-time
     * change would otherwise go unnoticed until an agent happened to check — SMS every agent at
     * the branch (best-effort, same "never blocks the caller" contract as {@link #notifyCollection}:
     * a gateway failure here must never fail the schedule update itself) and leave a BranchNotice
     * row the mobile app polls, so the change is visible even to an agent who missed the SMS or
     * had the app closed at the time. Fires for either an openTime or a closeTime change (or
     * both) — whichever half of the window actually moved, agents who log collections against it
     * need to know.
     */
    public void notifyBranchScheduleChange(UUID branchId, String branchName, LocalTime newOpenTime, LocalTime newCloseTime,
                                            boolean openTimeChanged, boolean closeTimeChanged) {
        String message;
        if (openTimeChanged && closeTimeChanged) {
            message = branchName + ": today's hours changed to " + newOpenTime + "-" + newCloseTime + ".";
        } else if (closeTimeChanged) {
            message = branchName + ": today's closing time changed to " + newCloseTime + ".";
        } else {
            message = branchName + ": today's opening time changed to " + newOpenTime + ".";
        }

        branchNoticeRepository.save(BranchNotice.builder()
                .id(UUID.randomUUID())
                .branchId(branchId)
                .message(message)
                .createdAt(Instant.now())
                .build());

        for (String phone : agentDirectoryService.findAgentPhonesByBranch(branchId)) {
            smsGatewayFactory.getActiveGateway().send(phone, message)
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(result -> { }, error -> { });
        }
    }

    /** UC-15: the caller's own branch's most recent operational notices, newest first — polled by the mobile app (no push infrastructure exists here, same reasoning as SOS acknowledgement). */
    public List<BranchNoticeResponse> listRecentNoticesForBranch(UUID branchId) {
        return branchNoticeRepository.findTop10ByBranchIdOrderByCreatedAtDesc(branchId).stream()
                .map(notice -> BranchNoticeResponse.builder()
                        .id(notice.getId())
                        .message(notice.getMessage())
                        .createdAt(notice.getCreatedAt())
                        .build())
                .toList();
    }

    private PreparedNotification prepare(UUID collectionId, UUID callerAgentId, boolean french) {
        CollectionSummary collection = collectionDirectoryService.findById(collectionId);
        if (!collection.agentId().equals(callerAgentId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot notify for a collection recorded by another agent");
        }
        String phone = clientDirectoryService.findPhone(collection.clientId());
        AgentReceiptInfo agent = agentDirectoryService.findReceiptInfo(callerAgentId);
        ClientReceiptInfo client = clientDirectoryService.findReceiptInfo(collection.clientId());
        List<DenominationLineView> denominationLines = collectionDirectoryService.findDenominationLines(collectionId);
        String mfiName = mfiSettingsService.getName();
        String formattedDate = MESSAGE_DATE_FORMAT.format(collection.collectedAt());
        String message = mfiName + ": depot de " + collection.amountXaf() + " XAF recu le "
                + formattedDate + " (agent " + agent.employeeCode() + "). Merci.";
        String receiptText = ReceiptTemplateComposer.compose(mfiName, agent.branchName(), collection.collectedAt(),
                agent.employeeCode(), agent.fullName(), client.mfiMemberNo(), client.fullName(), collection.amountXaf(),
                denominationLines, collection.deviceTxId(), french);
        ReceiptDataResponse receiptData = ReceiptDataComposer.compose(agent.branchName(), collection.collectedAt(),
                agent.employeeCode(), agent.fullName(), client.mfiMemberNo(), client.fullName(), collection.amountXaf(),
                denominationLines, collection.deviceTxId(), french);
        return new PreparedNotification(collection, phone, message, receiptText, receiptData);
    }

    private NotificationLogResponse persist(PreparedNotification prepared, SmsSendResult result, boolean printedReceipt) {
        NotificationLog log = NotificationLog.builder()
                .id(UUID.randomUUID())
                .collectionId(prepared.collection().id())
                .channel(NotificationChannel.SMS)
                .status(result.success() ? NotificationStatus.SENT : NotificationStatus.FAILED)
                .recipientPhone(prepared.phone())
                .providerReference(result.providerReference())
                .errorMessage(result.errorMessage())
                .printedReceipt(printedReceipt)
                .sentAt(Instant.now())
                .build();
        notificationLogRepository.save(log);
        return toResponse(log, prepared.receiptText(), prepared.receiptData());
    }

    private NotificationLogResponse toResponse(NotificationLog log, String receiptText, ReceiptDataResponse receiptData) {
        return NotificationLogResponse.builder()
                .id(log.getId())
                .collectionId(log.getCollectionId())
                .channel(log.getChannel().name())
                .status(log.getStatus().name())
                .printedReceipt(log.isPrintedReceipt())
                .sentAt(log.getSentAt())
                .receiptText(receiptText)
                .receiptData(receiptData)
                .build();
    }

    /**
     * Audit trail for a collection's notification attempts. {@code callerAgentId == null} means
     * the caller is Back-Office (any admin role) and skips the ownership check; a non-null id
     * enforces the same "only the agent who recorded the collection" rule {@link #notifyCollection}
     * uses. {@code receiptText} isn't persisted (it's regenerable, not audit data) so past entries
     * come back with it unset — the audit value here is the delivery status/timestamps, not
     * reconstructing historical message text.
     */
    public List<NotificationLogResponse> listForCollection(UUID collectionId, UUID callerAgentId) {
        if (callerAgentId != null) {
            CollectionSummary collection = collectionDirectoryService.findById(collectionId);
            if (!collection.agentId().equals(callerAgentId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view notifications for a collection recorded by another agent");
            }
        }
        return notificationLogRepository.findByCollectionIdOrderBySentAtDesc(collectionId).stream()
                .map(log -> toResponse(log, null, null))
                .toList();
    }

    private record PreparedNotification(CollectionSummary collection, String phone, String message, String receiptText, ReceiptDataResponse receiptData) {
    }
}
