package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogResponse {
    private UUID id;
    private UUID collectionId;
    private String channel;
    private String status;
    private boolean printedReceipt;
    private Instant sentAt;
    /** BR-Notif-01-compliant receipt text (MFI name, amount, date, agent ID) for the mobile app to hand to its Bluetooth thermal printer SDK. */
    private String receiptText;
    /** Structured fields for the mobile app's downloadable PDF receipt — see ReceiptDataComposer. Unset (like receiptText) on past audit-log entries. */
    private ReceiptDataResponse receiptData;
}
