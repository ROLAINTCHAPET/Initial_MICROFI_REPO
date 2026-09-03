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
 * A single cash deposit collected by an agent. Offline-safe: {@code (agentId, deviceTxId)} is
 * unique so a retried offline sync never double-counts. lat/lon are mandatory (BR-05, FR-12) —
 * the server is the final authority on the GPS gate, not just the mobile client.
 */
@Entity
@Table(name = "collection", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Collection {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID agentId;

    @Column(nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private long amountXaf;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    private Float accuracyM;

    /** Reverse-geocoded place name for the captured lat/lon (best-effort — null if the geocoding lookup failed). */
    private String locationName;

    @Column(nullable = false)
    private Instant collectedAt;

    @Column(nullable = false)
    @Builder.Default
    private String syncStatus = "SYNCED";

    @Column(nullable = false)
    private String deviceTxId;

    /** Which physical terminal recorded this collection (see Terminal) — distinct from deviceTxId, which is only an idempotency key. Boxed/nullable, same reasoning as failedPinAttempts, so ddl-auto=update doesn't need to backfill an already-populated table. */
    private String terminalId;

    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Null until this collection's amount has actually been counted in a cash reconciliation
     * (see OfjService#reconcile) — deliberately NOT the same thing as {@link #collectedAt} falling
     * within "today". An offline agent can sync a collection days after collecting it; its
     * amount must still reach exactly one reconciliation, whichever one runs after it arrives,
     * not the calendar day it happened to be collected on (which may already be closed, or may
     * never see this collection at all under a same-day-only window).
     */
    private Instant reconciledAt;

    /** Which {@code OfjAgentLine} counted this collection — lets CBS export post exactly the collections that were just reconciled, not everything with a matching calendar date. Stamped immediately at cashier-submit time regardless of {@link #reconciliationStatus}, so export/branch-closing never wait on agent confirmation. */
    private UUID reconciledInLineId;

    /** Distinct from {@link #reconciledAt} being non-null — see {@link CollectionReconciliationStatus}'s doc for why the two are decoupled. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CollectionReconciliationStatus reconciliationStatus = CollectionReconciliationStatus.UNRECONCILED;

    /** Null until {@link #reconciliationStatus} reaches {@code CONFIRMED} — audit-trail only, no behavioral branching. */
    @Enumerated(EnumType.STRING)
    private CollectionConfirmedBy confirmedBy;

    /** Null until {@code OfjService#postCollectionsToLedger} actually posts this collection to the CBS — the signal a rejection-approval uses to decide whether a real CBS reversal is needed, or just a local void. */
    private Instant exportedAt;

    /** The CBS's own reference for this posting (from {@code MiddlewareTransactionPostResult#postedReferences}) — needed to reverse the exact transaction later, not just "some collection for this agent." */
    private String cbsTransactionRef;

    /** Set once a {@code CollectionRejectionRequest} against this collection is approved — the collection is excluded from every downstream financial view from this point on, but the row itself is kept (never deleted) for the audit trail. */
    private Instant voidedAt;
}
