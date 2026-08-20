package com.microfi.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-09 audit trail: architecture.txt's {@code notification_log} (id, collection_id, channel,
 * status, sent_at), extended with the fields actually needed to act on a failure or dispute later
 * — {@code providerReference}/{@code errorMessage} for troubleshooting a specific gateway call,
 * {@code recipientPhone} because a client's phone on file can change after the fact, and
 * {@code printedReceipt} for the "receipt flag" the {@code POST /collections/{id}/notify} endpoint
 * purpose describes (whether the agent's app also printed a Bluetooth thermal receipt — a
 * device-local action this service can't perform, only record).
 */
@Entity
@Table(name = "notification_log", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID collectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    private String recipientPhone;

    private String providerReference;

    private String errorMessage;

    @Column(nullable = false)
    private boolean printedReceipt;

    @Column(nullable = false)
    private Instant sentAt;
}
