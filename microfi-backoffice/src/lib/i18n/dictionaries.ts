import "server-only";
import en from "./dictionaries/en.json";
import fr from "./dictionaries/fr.json";

export type Locale = "en" | "fr";
export type Dictionary = typeof en;

export const locales: Locale[] = ["en", "fr"];

export function isLocale(value: string): value is Locale {
  return (locales as string[]).includes(value);
}

// Both locales are bundled statically (not dynamically imported per-request) — these dictionaries
// are tiny compared to the rest of this Server-Component-only app's per-request payload. Typing
// this map as Record<Locale, Dictionary> (rather than `typeof en` inferred from the object
// literal) makes fr.json structurally match en.json's shape a compile-time check — a missing or
// mistyped key in the French file fails the build instead of surfacing as `undefined` in prod.
const dictionaries: Record<Locale, Dictionary> = { en, fr };

export function getDictionary(locale: Locale): Dictionary {
  return dictionaries[locale];
}
