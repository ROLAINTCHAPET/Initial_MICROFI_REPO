package com.microfi.registration.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-side backstop for the same CEMAC national ID / tax ID (NIU/NIF) format specs enforced
 * client-side in the Back-Office wizard (see {@code kycFormats.ts}) — defense-in-depth, not the
 * only check. Verified against each country's issuing authority (Cameroon DGI, Gabon DGI/e-Tax,
 * Congo Ministry of Finances, Chad DGI, CAR Ministère des Finances, Equatorial Guinea Ministerio
 * de Hacienda). Several formats are unions of a legacy and a new/digital scheme, since CEMAC's
 * regional biometric harmonization push has been actively changing them — Cameroon's legacy
 * 9-digit CNI still coexists with the new 10-character digital CNI; Chad's legacy 7-digit-plus-
 * check-letter NIF coexists with a new 13-character alphanumeric one. Congo's NIU format is
 * officially undisclosed (centrally stored with biometric data, no public structure) — left on
 * the generic fallback rather than a fabricated pattern. A phone number outside these 6 CEMAC
 * dial codes — or either field left blank, since both are optional — always passes: deliberately
 * generous rather than a hard lock, so an unmatched, undisclosed, or future format never silently
 * blocks a legitimate submission.
 */
@Component
public class KycFormatValidator {

    private record FormatSpec(Pattern nationalId, Pattern taxId) {
    }

    private static final FormatSpec FALLBACK =
            new FormatSpec(Pattern.compile("^[A-Z0-9]{7,17}$"), Pattern.compile("^[A-Z0-9]{7,17}$"));

    private static final Map<String, FormatSpec> BY_DIAL_CODE = Map.of(
            "+237", new FormatSpec(Pattern.compile("^(\\d{9}|[A-Z0-9]{10})$"), Pattern.compile("^[PM]\\d{12}[A-Z]$")),
            "+241", new FormatSpec(Pattern.compile("^[A-Z0-9]{14}$"), Pattern.compile("^[A-Z]?\\d{12,13}$")),
            "+242", new FormatSpec(Pattern.compile("^\\d{7,9}$"), Pattern.compile("^[A-Z0-9]{7,17}$")),
            "+235", new FormatSpec(Pattern.compile("^[A-Z0-9]{10}$"), Pattern.compile("^(\\d{7}[A-Z]|[A-Z0-9]{13})$")),
            "+236", new FormatSpec(Pattern.compile("^[A-Z0-9]{8,10}$"), Pattern.compile("^[A-Z0-9]{13}$")),
            "+240", new FormatSpec(Pattern.compile("^\\d{8,9}$"), Pattern.compile("^[A-Z0-9]{8,10}$"))
    );

    public boolean isValidNationalId(String value, String phone) {
        return value == null || value.isBlank() || spec(phone).nationalId.matcher(clean(value)).matches();
    }

    public boolean isValidTaxId(String value, String phone) {
        return value == null || value.isBlank() || spec(phone).taxId.matcher(clean(value)).matches();
    }

    private FormatSpec spec(String phone) {
        if (phone == null) {
            return FALLBACK;
        }
        return BY_DIAL_CODE.entrySet().stream()
                .filter(e -> phone.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(FALLBACK);
    }

    private String clean(String value) {
        return value.replaceAll("[\\s-]", "").toUpperCase();
    }
}
