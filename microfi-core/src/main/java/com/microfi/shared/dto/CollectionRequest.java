package com.microfi.shared.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class CollectionRequest {

    @NotNull
    private UUID clientId;

    @Positive
    private long amountXaf;

    /**
     * BR-05 / FR-12: GPS is mandatory. Wrapper types (not primitives) so a missing lat/lon in the
     * payload fails validation instead of silently defaulting to 0.0 (Null Island).
     */
    @NotNull(message = "lat is required: collection is blocked without a valid GPS fix (BR-05)")
    private Double lat;

    @NotNull(message = "lon is required: collection is blocked without a valid GPS fix (BR-05)")
    private Double lon;

    private Float accuracyM;

    @NotNull
    private Instant collectedAt;

    @NotBlank
    private String deviceTxId;

    /** Which physical terminal recorded this collection (see Terminal) — device identity is distinct from deviceTxId, which is only an idempotency key. */
    @NotBlank
    private String terminalId;

    /** Transaction confirmation — checked against the agent's own transaction PIN, independent of login (see AgentDirectoryService#verifyTransactionPin). */
    @NotBlank
    private String pin;

    /** FR-08: mandatory above the configured threshold; sum must equal amountXaf exactly (BR-02). */
    @Valid
    private List<DenominationLineDto> denominationLines;

    /**
     * Offline Field Collection Security Algorithm v1.1 §5-7 hash-chain fields — all nullable at
     * this DTO level so a pre-chain app build (or a login that never bound an installation) keeps
     * working unchanged; see CollectionService's back-compat rule 0. When {@link #collectionCounter}
     * is present, the other three must be too, or the chain-validation rule table rejects the
     * request outright rather than treating a partial submission as "no chain data at all."
     */
    private String installationId;
    private Long collectionCounter;
    private String previousHash;
    private String currentHash;
    private String signature;
}
