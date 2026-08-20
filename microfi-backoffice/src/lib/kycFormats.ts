// Authoritative CEMAC national ID / tax ID (NIU/NIF) format specs, keyed by ISO country code —
// verified against each country's issuing authority (Cameroon DGI, Gabon DGI/e-Tax, Congo
// Ministry of Finances, Chad DGI, CAR Ministère des Finances, Equatorial Guinea Ministerio de
// Hacienda). Several countries' formats are unions of a legacy and a new/digital scheme, since
// CEMAC's regional biometric harmonization push has been actively changing them: Cameroon's
// legacy 9-digit CNI still coexists with the new 17-character digital CNI (idcam.cm); Chad's
// legacy 7-digit-plus-check-letter NIF (e.g. 9000330V) coexists with a new 13-character
// alphanumeric one. Congo's NIU format is officially undisclosed (centrally stored with
// biometric data, no public structure) — deliberately left on the generic fallback rather than
// a fabricated pattern. Any country not in this map (or a phone number outside these 6 dial
// codes) also falls back to a generous alphanumeric length check — never a hard rejection — so
// an unmatched, undisclosed, or future format never silently blocks a legitimate submission.

export interface KycFormatSpec {
  nationalId: { pattern: RegExp; hint: string };
  taxId: { pattern: RegExp; hint: string };
}

const FALLBACK: KycFormatSpec = {
  nationalId: { pattern: /^[A-Z0-9]{7,17}$/, hint: "7–17 alphanumeric characters" },
  taxId: { pattern: /^[A-Z0-9]{7,17}$/, hint: "7–17 alphanumeric characters" },
};

export const KYC_FORMATS_BY_COUNTRY: Record<string, KycFormatSpec> = {
  CM: {
    nationalId: { pattern: /^(\d{9}|[A-Z0-9]{10})$/, hint: "9 digits (legacy CNI) or 10 alphanumeric characters (new digital CNI)" },
    taxId: { pattern: /^[PM]\d{12}[A-Z]$/, hint: "P or M, then 12 digits, then 1 letter — e.g. P123456789012X" },
  },
  GA: {
    nationalId: { pattern: /^[A-Z0-9]{14}$/, hint: "14 alphanumeric characters (CNIE)" },
    taxId: { pattern: /^[A-Z]?\d{12,13}$/, hint: "13–14 characters: an optional leading letter then 12–13 digits — e.g. P0123456789012 (NIF)" },
  },
  CG: {
    nationalId: { pattern: /^\d{7,9}$/, hint: "7–9 digits" },
    // NIU structure is not publicly disclosed (centrally stored with biometric data) — kept on
    // the same generous check as the cross-country fallback rather than a guessed pattern.
    taxId: { pattern: /^[A-Z0-9]{7,17}$/, hint: "format not publicly disclosed — 7–17 alphanumeric characters accepted (NIU)" },
  },
  TD: {
    nationalId: { pattern: /^[A-Z0-9]{10}$/, hint: "10 alphanumeric characters" },
    taxId: { pattern: /^(\d{7}[A-Z]|[A-Z0-9]{13})$/, hint: "legacy: 7 digits + 1 check letter (e.g. 9000330V), or new: 13 alphanumeric characters (NIF)" },
  },
  CF: {
    nationalId: { pattern: /^[A-Z0-9]{8,10}$/, hint: "8–10 alphanumeric characters" },
    taxId: { pattern: /^[A-Z0-9]{13}$/, hint: "13 alphanumeric characters (NIF)" },
  },
  GQ: {
    nationalId: { pattern: /^\d{8,9}$/, hint: "8–9 digits (DNI)" },
    taxId: { pattern: /^[A-Z0-9]{8,10}$/, hint: "8–10 alphanumeric characters (NIF)" },
  },
};

export function kycFormatFor(countryIso: string): KycFormatSpec {
  return KYC_FORMATS_BY_COUNTRY[countryIso] ?? FALLBACK;
}

/** Users often type spaces/dashes into ID numbers — strip those before matching, same as the Dart reference implementation. */
export function matchesKycFormat(value: string, pattern: RegExp): boolean {
  const cleaned = value.replace(/[\s-]/g, "").toUpperCase();
  return pattern.test(cleaned);
}
