package com.microfi.transactions.domain;

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
 * An agent's request to void one of their own collections for error (wrong amount, wrong client,
 * duplicate entry, etc.) — mirrors {@link VarianceDebt}'s immutable-original-plus-decision-trail
 * shape: {@code collectionId}/{@code agentId}/{@code reason}/{@code requestedAt} never change once
 * created, and the decision (approve/deny) is its own audit trail layered on top, not an edit.
 * Approval requires mandatory proof (see {@code CollectionRejectionProofStorageService}); denial
 * only requires a reason, mirroring {@code RegistrationApplicationController#reject}.
 */
@Entity
@Table(name = "collection_rejection_request", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionRejectionRequest {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID collectionId;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private String reason;

    /** Snapshot of {@code Collection#amountXaf} at request time — the amount the agent says is wrong, kept even if the collection itself were ever inspected later, so the reviewer always sees exactly what was recorded without a second lookup. */
    private long actualAmountXaf;

    /** What the agent says the amount should actually have been — optional, since not every rejection reason is amount-related (wrong client, duplicate entry, etc.). */
    private Long expectedAmountXaf;

    @Builder.Default
    private Instant requestedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CollectionRejectionStatus status = CollectionRejectionStatus.PENDING;

    /** Populated only once {@link #status} leaves {@code PENDING}. */
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String decisionReason;

    /** Populated only when {@link #status} is {@code APPROVED} — mandatory at approval time, never present on a denial. */
    private String proofPath;
}
