"use client";

import { useEffect } from "react";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

// Root-level fallback for anything (dashboard)/error.tsx can't reach — DashboardLayout's own
// getSession()/data fetching, or the login page. An error boundary only catches throws from its
// own segment's page and nested layouts below it, never its own paired layout, so this is the
// backstop for that gap. Deliberately minimal — nothing above this level guarantees the design
// tokens' surrounding chrome (Header/Sidebar) rendered successfully.
export default function RootError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const dict = useDictionary();
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="min-h-screen flex items-center justify-center p-6">
      <div className="max-w-md w-full flex flex-col items-center text-center gap-4 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-8">
        <div className="h-14 w-14 rounded-full bg-error-container flex items-center justify-center">
          <Icon name="warning" className="size-7 text-on-error-container" />
        </div>
        <div>
          <h2 className="text-lg font-bold text-on-surface">{dict.errors.rootTitle}</h2>
          <p className="text-sm text-on-surface-variant mt-1">{dict.errors.description}</p>
        </div>
        {error.digest && <p className="text-xs text-outline font-mono">{t(dict.errors.reference, { digest: error.digest })}</p>}
        <button
          onClick={reset}
          className="h-11 px-6 bg-primary text-on-primary font-semibold text-sm rounded-[var(--radius-md)] cursor-pointer hover:bg-primary/90 transition-colors"
        >
          {dict.errors.tryAgain}
        </button>
      </div>
    </div>
  );
}
