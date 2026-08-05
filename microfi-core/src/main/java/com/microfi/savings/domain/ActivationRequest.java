package com.microfi.savings.domain;

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
 * UC-19's two-party activation gate: neither the sponsoring agent nor the client can activate a
 * booklet unilaterally — an {@link com.microfi.savings.domain.AccessToken} is only issued once
 * <b>both</b> {@code sponsoredAt} (agent confirms, identifying the client by their login) and
 * {@code paidAt} (client confirms in their own authenticated session, re-entering their PIN) are
 * set, whichever order they happen in. A client has at most one {@code PENDING} request at a time,
 * and an agent may only have one {@code PENDING} request open at all (across every client) — see
 * {@code CollectionService.requireNoPendingActivation}. Since that's a hard block on the agent's
 * ability to collect any cash, a stuck request (client never confirms) needs a way out: an admin
 * can cancel it ({@code cancelledBy}/{@code cancelReason} set), or {@code ActivationRequestExpiryJob}
 * auto-expires it after sitting PENDING past the configured timeout.
 */
@Entity
@Table(name = "activation_request", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationRequest {

    @Id
    private UUID id;

    private UUID clientId;

    private UUID agentId;

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant sponsoredAt;

    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ActivationRequestStatus status = ActivationRequestStatus.PENDING;

    private Instant cancelledAt;

    /** Null for an auto-{@code EXPIRED} request — only set when an admin manually cancels one. */
    private UUID cancelledBy;

    private String cancelReason;
}
