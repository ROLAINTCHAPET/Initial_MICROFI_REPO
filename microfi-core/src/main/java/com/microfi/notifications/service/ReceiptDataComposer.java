package com.microfi.notifications.service;

import com.microfi.shared.dto.ReceiptDataResponse;
import com.microfi.shared.dto.ReceiptDenominationLineResponse;
import com.microfi.transactions.service.CollectionDirectoryService.DenominationLineView;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the structured fields behind the mobile app's downloadable PDF receipt (Graphical
 * Design/microfi_receipt_design.html) — a styled card, unlike {@link ReceiptTemplateComposer}'s
 * plain-text block for Bluetooth thermal printing. Both are built from the same underlying
 * collection/agent/client data in {@link NotificationService#prepare}; this one just shapes it
 * differently for a design that shows every canonical denomination (even at zero) rather than
 * skipping empty ones to save thermal paper.
 */
final class ReceiptDataComposer {

    /** Mirrors the mobile app's own denomination set (collection_stepper_screen.dart's _denominations) — the design's table always shows this full breakdown, not just what was actually counted. */
    private static final long[] CANONICAL_DENOMINATIONS = {10_000, 5_000, 2_000, 1_000, 500, 200, 100, 50, 25};

    private static final DateTimeFormatter DATE_FORMAT_EN =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_FORMAT_FR =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SIGNATURE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yy").withZone(ZoneOffset.UTC);

    private ReceiptDataComposer() {
    }

    static ReceiptDataResponse compose(String branchName, Instant collectedAt, String employeeCode,
                                        String agentFullName, String clientMemberNo, String clientFullName,
                                        long amountXaf, List<DenominationLineView> denominationLines, String deviceTxId,
                                        boolean french) {
        Map<Long, Integer> byFaceValue = new LinkedHashMap<>();
        for (long face : CANONICAL_DENOMINATIONS) {
            byFaceValue.put(face, 0);
        }
        for (DenominationLineView line : denominationLines) {
            byFaceValue.put(line.faceValueXaf(), line.quantity());
        }
        List<ReceiptDenominationLineResponse> lines = byFaceValue.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByKey().reversed())
                .map(e -> ReceiptDenominationLineResponse.builder()
                        .faceValueXaf(e.getKey())
                        .quantity(e.getValue())
                        .lineTotalXaf(e.getKey() * e.getValue())
                        .build())
                .toList();

        // "Twenty-Five Thousand" -> "Twenty-five thousand CFA francs" — sentence case, but "CFA"
        // stays an uppercase currency code rather than being folded by a blanket lowercase. French
        // mirrors the same sentence-case treatment: "vingt-cinq mille" -> "Vingt-cinq mille francs CFA".
        String numberWords = (french ? NumberToWordsConverter.toWordsFrench(amountXaf) : NumberToWordsConverter.toWords(amountXaf))
                .toLowerCase(Locale.ROOT);
        String sentenceCaseWords = Character.toUpperCase(numberWords.charAt(0)) + numberWords.substring(1)
                + (french ? " francs CFA" : " CFA francs");

        DateTimeFormatter dateFormat = french ? DATE_FORMAT_FR : DATE_FORMAT_EN;

        return ReceiptDataResponse.builder()
                .branchName(branchName)
                .dateFormatted(dateFormat.format(collectedAt) + " · " + TIME_FORMAT.format(collectedAt) + " UTC")
                .agentEmployeeCode(employeeCode)
                .agentShortName(ReceiptTemplateComposer.shortName(agentFullName))
                .clientMemberNo(clientMemberNo)
                .clientFullName(clientFullName)
                .amountXaf(amountXaf)
                .amountWords(sentenceCaseWords)
                .denominationLines(lines)
                .uniqueRef(deviceTxId)
                .signature("XAF · GEOTAG VALID · " + SIGNATURE_DATE_FORMAT.format(collectedAt))
                .build();
    }
}
