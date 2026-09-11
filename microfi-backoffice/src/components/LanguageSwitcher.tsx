"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useDictionary, useLocale } from "@/lib/i18n/I18nProvider";
import type { Locale } from "@/lib/i18n/dictionaries";

// Shown to the left of each language's label in the dropdown, so choosing between them is a
// visual pick, not just a text read — the closed button itself keeps the plain 🌐 glyph, since
// it represents "language" in general rather than one specific choice.
const LOCALE_FLAG: Record<Locale, string> = {
  en: "🇬🇧",
  fr: "🇫🇷",
};

export function LanguageSwitcher() {
  const router = useRouter();
  const locale = useLocale();
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  async function choose(next: Locale) {
    setOpen(false);
    if (next === locale) return;
    setSaving(true);
    await fetch("/api/locale", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ locale: next }),
    });
    router.refresh();
    setSaving(false);
  }

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((o) => !o)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        disabled={saving}
        title={dict.header.language}
        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-[var(--radius-full)] border-2 border-outline-variant text-xs font-bold text-primary cursor-pointer hover:bg-surface-container-low transition-colors duration-150 disabled:opacity-60"
      >
        <span aria-hidden="true">🌐</span>
        {locale.toUpperCase()}
      </button>

      {open && (
        <div className="panel-scale-in absolute right-0 top-full mt-2 w-36 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-2)] overflow-hidden z-30">
          <button
            onClick={() => choose("en")}
            className="w-full flex items-center gap-2 text-left px-4 py-2.5 text-sm text-on-surface cursor-pointer hover:bg-surface-container-low transition-colors"
          >
            <span aria-hidden="true">{LOCALE_FLAG.en}</span>
            {dict.header.languageEnglish}
          </button>
          <button
            onClick={() => choose("fr")}
            className="w-full flex items-center gap-2 text-left px-4 py-2.5 text-sm text-on-surface cursor-pointer hover:bg-surface-container-low transition-colors"
          >
            <span aria-hidden="true">{LOCALE_FLAG.fr}</span>
            {dict.header.languageFrench}
          </button>
        </div>
      )}
    </div>
  );
}
