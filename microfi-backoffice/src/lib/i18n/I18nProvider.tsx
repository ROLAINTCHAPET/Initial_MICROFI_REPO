"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { Dictionary, Locale } from "./dictionaries";

// Every page in this app is a Server Component, so most strings are looked up server-side via
// getDictionary(await getLocale()) directly — no context needed there. This provider exists only
// for the minority of components that are "use client" (Header, modals, workspaces with local
// state) and can't call the server-only getLocale()/getDictionary() themselves. The root layout
// resolves the locale and dictionary once, server-side, and hands them down as plain serializable
// props across this one client boundary.
const I18nContext = createContext<{ locale: Locale; dict: Dictionary } | null>(null);

export function I18nProvider({ locale, dict, children }: { locale: Locale; dict: Dictionary; children: ReactNode }) {
  return <I18nContext.Provider value={{ locale, dict }}>{children}</I18nContext.Provider>;
}

export function useDictionary(): Dictionary {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useDictionary() must be used within I18nProvider");
  return ctx.dict;
}

export function useLocale(): Locale {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error("useLocale() must be used within I18nProvider");
  return ctx.locale;
}
