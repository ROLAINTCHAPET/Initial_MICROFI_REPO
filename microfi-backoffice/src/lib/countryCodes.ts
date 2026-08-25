/**
 * The 6 CEMAC member states' calling codes — the platform's actual deployment zone, not a
 * generic country list. Shared by every phone-entry field in this app (registration wizard,
 * branch creation) so the list can't drift between them. `iso` doubles as the key into
 * kycFormats.ts, so National ID/Tax ID validation follows whichever country is selected.
 */
export const COUNTRY_CODES: { code: string; iso: string; flag: string; label: string }[] = [
  { code: "+237", iso: "CM", flag: "🇨🇲", label: "Cameroon" },
  { code: "+241", iso: "GA", flag: "🇬🇦", label: "Gabon" },
  { code: "+235", iso: "TD", flag: "🇹🇩", label: "Chad" },
  { code: "+236", iso: "CF", flag: "🇨🇫", label: "Central African Republic" },
  { code: "+242", iso: "CG", flag: "🇨🇬", label: "Congo" },
  { code: "+240", iso: "GQ", flag: "🇬🇶", label: "Equatorial Guinea" },
];
