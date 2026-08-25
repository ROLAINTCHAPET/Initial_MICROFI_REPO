"use client";

import { useEffect } from "react";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

// Catches anything thrown while rendering a dashboard page — most commonly apiFetch failing
// because Kong/microfi-core is unreachable or returned an error. Without this, Next.js has no
// boundary here at all: a Server Component throw crashes to the raw dev overlay in development,
// and to an unstyled generic crash screen in production — neither tells an ADMIN/BRANCH_MANAGER
// anything useful or gives them a way back in. The surrounding Header/Sidebar (DashboardLayout)
// keep rendering as normal; only this page's content area is replaced.
//
// Error messages reaching a Client Component error boundary are sanitized by Next.js in
// production (only `digest` survives) — so this deliberately shows one generic, actionable
// message rather than trying to parse/branch on the underlying error.
export default function DashboardError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const dict = useDictionary();
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="h-full flex items-center justify-center p-6">
      <div className="max-w-md w-full flex flex-col items-center text-center gap-4 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-8">
        <div className="h-14 w-14 rounded-full bg-error-container flex items-center justify-center">
          <Icon name="warning" className="size-7 text-on-error-container" />
        </div>
        <div>
          <h2 className="text-lg font-bold text-on-surface">{dict.errors.dashboardTitle}</h2>
          <p className="text-sm text-on-surface-variant mt-1">{dict.errors.description}</p>
        </div>
        {error.digest && <p className="text-xs text-outline font-mono">{t(dict.errors.reference, { digest: error.digest })}</p>}
        {process.env.NODE_ENV !== "production" && (
          <p className="text-xs text-danger-red font-mono break-all text-left w-full bg-error-container/30 rounded-[var(--radius-sm)] p-2">
            {error.message}
          </p>
        )}
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
