package com.microfi.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured fields backing the mobile app's downloadable PDF receipt (Graphical Design/
 * microfi_receipt_design.html) — a styled card, not the plain-text {@code receiptText} used for
 * Bluetooth thermal printing. The brand mark ("MICROFI") and tagline are fixed in that design, so
 * they're not carried here; everything below is the per-collection data the design has a slot for.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptDataResponse {
    private String branchName;
    /** Pre-formatted per the design's "03 Aug 2026 · 09:30 UTC" style, so the client never re-implements date formatting. */
    private String dateFormatted;
    private String agentEmployeeCode;
    private String agentShortName;
    private String clientMemberNo;
    private String clientFullName;
    private long amountXaf;
    /** Sentence case, e.g. "Twenty-five thousand CFA francs" — matches the design's italic amount-words line. */
    private String amountWords;
    /** Every canonical denomination (not just the ones actually counted), so the table always shows the full breakdown per the design. */
    private List<ReceiptDenominationLineResponse> denominationLines;
    private String uniqueRef;
    /** e.g. "XAF · GEOTAG VALID · 03-08-26". */
    private String signature;
}
