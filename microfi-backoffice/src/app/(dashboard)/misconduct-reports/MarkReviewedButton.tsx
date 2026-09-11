"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function MarkReviewedButton({ reportId }: { reportId: string }) {
  const router = useRouter();
  const dict = useDictionary();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick() {
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/misconduct-reports/${reportId}/mark-reviewed`, { method: "PATCH" });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.misconductReports.failedToMarkReviewed);
        return;
      }
      router.refresh();
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <button
        onClick={handleClick}
        disabled={loading}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[var(--radius-full)] border-2 border-outline-variant text-primary text-xs font-bold cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:bg-surface-container-low hover:scale-[1.03] active:scale-95 disabled:opacity-60 disabled:cursor-not-allowed"
      >
        {loading ? dict.misconductReports.markingReviewed : dict.misconductReports.markReviewedButton}
      </button>
      {error && <p role="alert" className="text-xs text-danger-red">{error}</p>}
    </div>
  );
}
