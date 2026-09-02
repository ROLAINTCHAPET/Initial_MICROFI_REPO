"use client";

import { useRouter } from "next/navigation";
import { useDictionary } from "@/lib/i18n/I18nProvider";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export function HistoryDateRangeFilter({
  branchId,
  tab,
  from,
  to,
  extraQuery = "",
}: {
  branchId: string;
  tab: "history" | "variance";
  from: string;
  to: string;
  extraQuery?: string;
}) {
  const dict = useDictionary();
  const router = useRouter();

  function push(nextFrom: string, nextTo: string) {
    router.push(`/ofj?branchId=${branchId}&tab=${tab}&from=${nextFrom}&to=${nextTo}${extraQuery}`);
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <label className="flex items-center gap-2 text-sm text-on-surface-variant">
        {dict.export.from}
        <input
          type="date"
          value={from}
          max={to}
          onChange={(e) => push(e.target.value, to)}
          className="border border-outline-variant rounded-[var(--radius-sm)] px-2 py-1 text-sm text-on-surface bg-surface"
        />
      </label>
      <label className="flex items-center gap-2 text-sm text-on-surface-variant">
        {dict.export.to}
        <input
          type="date"
          value={to}
          min={from}
          max={todayIso()}
          onChange={(e) => push(from, e.target.value)}
          className="border border-outline-variant rounded-[var(--radius-sm)] px-2 py-1 text-sm text-on-surface bg-surface"
        />
      </label>
    </div>
  );
}
