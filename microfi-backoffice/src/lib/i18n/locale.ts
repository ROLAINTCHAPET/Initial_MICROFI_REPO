import "server-only";
import { cookies } from "next/headers";
import { isLocale, type Locale } from "./dictionaries";

export const LOCALE_COOKIE = "microfi_locale";

// Defaults to French, not English: French is the one official language shared by all 6 CEMAC
// member states (Cameroon, Gabon, Congo, Chad, CAR, Equatorial Guinea) this product serves,
// unlike English, which is only co-official in Cameroon — same reasoning as the mobile app's
// LocalePreference default.
const DEFAULT_LOCALE: Locale = "fr";

/** Server Components / Route Handlers only — reads the (non-httpOnly, JS-writable) locale cookie. */
export async function getLocale(): Promise<Locale> {
  const store = await cookies();
  const value = store.get(LOCALE_COOKIE)?.value;
  return value && isLocale(value) ? value : DEFAULT_LOCALE;
}
