"use client";

// DESIGN.md: escrow ceiling bar — navy below 80%, amber from 80%, red + lock icon at 100%.
// Matches the "Escrow Wallet Level" / "Escrow Balance" bars in the agent grid and detail mockups.
import { Icon } from "./Icon";
import { ceilingUtilizationPct } from "@/lib/format";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function Gauge({ balanceXaf, ceilingXaf, compact = false }: { balanceXaf: number; ceilingXaf: number; compact?: boolean }) {
  const dict = useDictionary();
  const pct = ceilingUtilizationPct(balanceXaf, ceilingXaf);
  const barClass = pct >= 100 ? "bg-danger-red" : pct >= 80 ? "bg-warning-amber" : "bg-primary";
  const textClass = pct >= 100 ? "text-danger-red" : pct >= 80 ? "text-[#946200]" : "text-on-surface";

  return (
    <div className={compact ? "flex items-center gap-2 min-w-36" : "flex flex-col gap-1 w-full"}>
      {!compact && (
        <div className="flex justify-between text-xs tabular-nums">
          <span className={`font-semibold ${textClass}`}>{balanceXaf.toLocaleString()} XAF</span>
          <span className={`font-semibold ${textClass}`}>{pct}%</span>
        </div>
      )}
      <div className="flex items-center gap-2">
        <div
          className="flex-1 h-2 rounded-[var(--radius-full)] bg-surface-container-high overflow-hidden"
          role="progressbar"
          aria-valuenow={pct}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          <div className={`h-full ${barClass} transition-[width] duration-500 ease-out`} style={{ width: `${pct}%` }} />
        </div>
        {compact && <span className="text-xs text-text-slate tabular-nums w-10 text-right">{pct}%</span>}
        {pct >= 100 && (
          <span title={dict.common.gauge.ceilingReached} aria-label={dict.common.gauge.ceilingReachedAria}>
            <Icon name="lock" className="size-4 text-danger-red" />
          </span>
        )}
      </div>
    </div>
  );
}
