package com.microfi.notifications.domain;

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
 * An ADMIN/BRANCH_MANAGER-authored announcement to every client or every agent — network-wide
 * ({@code branchId == null}, ADMIN only) or scoped to one branch. Generalizes {@link BranchNotice}
 * (branch-only, agent-only, no sender tracking) to also reach clients and to support a
 * network-wide send. Polled by the mobile app the same way BranchNotice already is (no push
 * infrastructure in this app); SMS is sent alongside for immediate reach even with the app closed.
 */
@Entity
@Table(name = "broadcast_message", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastMessage {

    @Id
    private UUID id;

    private UUID senderAdminId;

    private String senderLabel;

    @Enumerated(EnumType.STRING)
    private BroadcastAudience audience;

    /** Null means network-wide (ADMIN only) — a BRANCH_MANAGER's send is always scoped to their own branch. */
    private UUID branchId;

    private String message;

    /** How many phones were SMS'd at send time — persisted so Back-Office history can show it too, not just the immediate send response. */
    private int recipientCount;

    private Instant createdAt;
}
