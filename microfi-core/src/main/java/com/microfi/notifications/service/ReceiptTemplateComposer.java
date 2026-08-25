package com.microfi.notifications.service;

import com.microfi.transactions.service.CollectionDirectoryService.DenominationLineView;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the UC-09 Bluetooth thermal receipt exactly per the UI/UX Design Handoff's "Bluetooth
 * Thermal Receipt Template" (Section 11) — one plain-text block so it renders identically whether
 * printed (58 mm/32-column monospace) or downloaded as a .txt file. Width is fixed at 32 columns,
 * the standard character count for a 58 mm printer at normal font (matches PaperSize.mm58 already
 * used by the mobile app's printer_service.dart); header/footer lines are manually centered rather
 * than relying on the printer's own alignment feature, so the same text looks right in both places.
 * <p>
 * {@code french} selects the agent's chosen receipt language (mirrors the mobile app's own
 * LocalePreference) — labels/section headers/number-words translate, but the MFI's own name,
 * the brand line "MICROFI COLLECT", XAF amounts, dates, and reference codes stay identical in
 * either language, since those aren't language-dependent content.
 */
final class ReceiptTemplateComposer {

    private static final int WIDTH = 32;
    private static final String MAJOR_RULE = "=".repeat(WIDTH);
    private static final String MINOR_RULE = "-".repeat(WIDTH);
    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SIGNATURE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyy").withZone(ZoneOffset.UTC);

    private ReceiptTemplateComposer() {
    }

    static String compose(String mfiName, String branchName, Instant collectedAt, String employeeCode,
                           String agentFullName, String clientMemberNo, String clientFullName, long amountXaf,
                           List<DenominationLineView> denominationLines, String deviceTxId, boolean french) {
        StringBuilder r = new StringBuilder();
        r.append(MAJOR_RULE).append('\n');
        r.append(center("MICROFI COLLECT")).append('\n');
        r.append(center(mfiName.toUpperCase(Locale.ROOT))).append('\n');
        r.append(center(french ? "ENCAISSEMENT NUMÉRIQUE CEMAC" : "DIGITAL CASH COLLECTION CEMAC")).append('\n');
        r.append(MAJOR_RULE).append('\n');
        r.append(french ? "Agence : " : "Branch : ").append(branchName).append('\n');
        r.append("Date   : ").append(RECEIPT_DATE_FORMAT.format(collectedAt)).append(" UTC").append('\n');
        r.append("Agent  : ").append(employeeCode).append(" (").append(shortName(agentFullName)).append(")").append('\n');
        r.append(MINOR_RULE).append("\n\n");
        r.append(french ? "ID Client : " : "Client ID : ").append(clientMemberNo).append('\n');
        r.append(french ? "Nom       : " : "Name      : ").append(clientFullName.toUpperCase(Locale.ROOT)).append('\n');
        r.append(MINOR_RULE).append("\n\n");
        r.append(french ? "Montant Encaissé : " : "Amount Collected : ").append(grouped(amountXaf)).append(" XAF").append('\n');
        r.append("(").append(french ? NumberToWordsConverter.toWordsFrench(amountXaf) : NumberToWordsConverter.toWords(amountXaf))
                .append(french ? " Francs CFA)" : " CFA Francs)").append("\n\n");
        r.append(french ? "Mode de Paiement : ESPÈCES" : "Payment Method  : CASH").append('\n');
        r.append(french ? "Statut du Ticket : ENREGISTRÉ (*)" : "Ticket Status   : RECORDED (*)").append("\n\n");
        r.append(MINOR_RULE).append('\n');
        r.append(center(french ? "DÉTAIL DES COUPURES (XAF)" : "DENOMINATION BREAKDOWN (XAF)")).append('\n');
        r.append(MINOR_RULE).append('\n');
        for (DenominationLineView line : denominationLines) {
            if (line.quantity() <= 0) {
                continue;
            }
            long lineTotal = line.faceValueXaf() * line.quantity();
            r.append(String.format("%6s x %-2d = %8s", grouped(line.faceValueXaf()), line.quantity(), grouped(lineTotal))).append('\n');
        }
        r.append(MINOR_RULE).append("\n\n");
        r.append(french ? "Réf. Unique : " : "Unique Ref : ").append(deviceTxId).append(french ? " (UUID local)" : " (local UUID)").append('\n');
        r.append(french ? "Signature Numérique Sécurisée :" : "Secure Digital Signature:").append('\n');
        r.append("[XAF-GEOTAG-VALID-").append(SIGNATURE_DATE_FORMAT.format(collectedAt)).append("]").append("\n\n");
        r.append(MINOR_RULE).append('\n');
        if (french) {
            r.append("(*) Ce reçu électronique fait office").append('\n');
            r.append("de preuve de votre dépôt en cas de").append('\n');
            r.append("panne réseau temporaire.").append('\n');
        } else {
            r.append("(*) This electronic receipt serves as").append('\n');
            r.append("proof of your deposit in case of").append('\n');
            r.append("temporary network outage.").append('\n');
        }
        r.append(MAJOR_RULE).append('\n');
        r.append(center(french ? "MERCI POUR VOTRE CONFIANCE" : "THANK YOU FOR YOUR TRUST")).append('\n');
        r.append(MAJOR_RULE);
        return r.toString();
    }

    private static String center(String text) {
        if (text.length() >= WIDTH) {
            return text;
        }
        int totalPad = WIDTH - text.length();
        int left = totalPad / 2;
        return " ".repeat(left) + text;
    }

    /** "Fouda Marie" -> "Fouda M." — surname in full, given name(s) reduced to initials, matching the template's "Fouda M." example. Package-visible: also used by {@link ReceiptDataComposer}. */
    static String shortName(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length <= 1) {
            return fullName;
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(' ').append(Character.toUpperCase(parts[i].charAt(0))).append('.');
            }
        }
        return sb.toString();
    }

    /** Package-visible: also used by {@link ReceiptDataComposer}. */
    static String grouped(long n) {
        String digits = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            count++;
            if (count % 3 == 0 && i != 0) {
                sb.append(' ');
            }
        }
        return (n < 0 ? "-" : "") + sb.reverse();
    }
}
