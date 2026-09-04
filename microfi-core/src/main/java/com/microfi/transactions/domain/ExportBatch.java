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
 * FR-18 — record of a daily CBS export file and the middleware's acknowledgement. A session can
 * now have more than one of these over the course of a day (export became repeatable/idempotent,
 * see CollectionRepository's confirmed-and-unexported query) — {@code trigger} is what tells the
 * audit trail/a future reports screen why each individual run happened.
 */
@Entity
@Table(name = "export_batch", schema = "core")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportBatch {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ofjId;

    @Column(nullable = false)
    private String fileUri;

    @Column(nullable = false)
    private String format;

    @Builder.Default
    private Instant generatedAt = Instant.now();

    private String ackStatus;

    /** Nullable for pre-migration rows created before this field existed. */
    @Enumerated(EnumType.STRING)
    private ExportTrigger trigger;
}
