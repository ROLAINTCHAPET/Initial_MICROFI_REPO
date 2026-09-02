"use client";

import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import { Badge } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import { formatCompactXaf } from "@/lib/format";
import type { AgentStatus } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

export interface AgentRow {
  id: string;
  fullName: string;
  employeeCode: string;
  branchName: string;
  status: AgentStatus;
  // Cash actually collected today (not yet remitted) — this is what the ceiling gate
  // (BR-03) blocks against, distinct from the funded guarantee shown as "Limit".
  collectedTodayXaf: number | null;
  ceilingXaf: number | null;
  pct: number | null;
  nearLimit: boolean;
}

type Filter = "all" | "nearCeiling" | "suspended";

function initials(name: string) {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? "")).toUpperCase();
}

export function AgentsExplorer({ rows, actions }: { rows: AgentRow[]; actions?: ReactNode }) {
  const dict = useDictionary();
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("all");
  // "All branches" (grouped, current behavior) vs one specific branch — a real selection, not
  // just a visual grouping, so a manager scanning one branch never has to eyeball past every
  // other branch's rows to find where theirs starts and ends.
  const [branch, setBranch] = useState<string>("all");

  const branchNames = useMemo(() => {
    const names = new Set(rows.map((r) => r.branchName || dict.agents.unassignedBranch));
    return Array.from(names).sort((a, b) => a.localeCompare(b));
  }, [rows, dict.agents.unassignedBranch]);

  const stats = useMemo(
    () => ({
      active: rows.filter((r) => r.status === "ACTIVE").length,
      nearCeiling: rows.filter((r) => r.nearLimit).length,
      suspended: rows.filter((r) => r.status === "SUSPENDED").length,
      collectedToday: rows.reduce((sum, r) => sum + (r.collectedTodayXaf ?? 0), 0),
    }),
    [rows]
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows
      .filter((r) => {
        if (branch !== "all" && (r.branchName || dict.agents.unassignedBranch) !== branch) return false;
        if (filter === "nearCeiling" && !r.nearLimit) return false;
        if (filter === "suspended" && r.status !== "SUSPENDED") return false;
        if (q && !`${r.fullName} ${r.employeeCode} ${r.branchName}`.toLowerCase().includes(q)) return false;
        return true;
      })
      .sort((a, b) => a.fullName.localeCompare(b.fullName));
  }, [rows, query, filter, branch, dict.agents.unassignedBranch]);

  // Grouped by branch rather than one long undifferentiated list — makes it easy to scan a
  // single branch's agents without hunting through every other branch's rows in between.
  const grouped = useMemo(() => {
    const byBranch = new Map<string, AgentRow[]>();
    for (const row of filtered) {
      const key = row.branchName || dict.agents.unassignedBranch;
      const bucket = byBranch.get(key);
      if (bucket) bucket.push(row);
      else byBranch.set(key, [row]);
    }
    return Array.from(byBranch.entries()).sort((a, b) => a[0].localeCompare(b[0]));
  }, [filtered, dict.agents.unassignedBranch]);

  function toggleFilter(next: Filter) {
    setFilter((cur) => (cur === next ? "all" : next));
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="flex items-center gap-3 flex-wrap flex-1 min-w-0">
          <div className="relative max-w-sm w-full">
            <Icon name="search" className="absolute left-3 top-1/2 -translate-y-1/2 size-5 text-outline pointer-events-none" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              type="text"
              placeholder={dict.agents.searchPlaceholder}
              className="w-full h-11 pl-10 pr-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm focus:outline-none focus:border-primary transition-colors"
            />
          </div>
          {branchNames.length > 1 && (
            <div className="flex items-center gap-2">
              <Icon name="location-on" className="size-5 text-primary shrink-0" />
              <select
                value={branch}
                onChange={(e) => setBranch(e.target.value)}
                className="h-11 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm cursor-pointer focus:outline-none focus:border-primary transition-colors min-w-0 max-w-full"
              >
                <option value="all">{dict.agents.allBranches}</option>
                {branchNames.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
        {actions}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <StatCard label={dict.agents.statTotalActive} value={stats.active.toLocaleString()} />
        <StatCard
          label={dict.agents.statNearCeiling}
          value={stats.nearCeiling.toLocaleString()}
          hint={dict.agents.statNearCeilingHint}
          valueClass={stats.nearCeiling > 0 ? "text-tertiary-fixed-dim" : undefined}
          active={filter === "nearCeiling"}
          onClick={stats.nearCeiling > 0 ? () => toggleFilter("nearCeiling") : undefined}
        />
        <StatCard
          label={dict.agents.statSuspended}
          value={stats.suspended.toLocaleString()}
          hint={dict.agents.statSuspendedHint}
          valueClass={stats.suspended > 0 ? "text-error" : undefined}
          active={filter === "suspended"}
          onClick={stats.suspended > 0 ? () => toggleFilter("suspended") : undefined}
        />
        <StatCard label={dict.agents.statCollectedToday} value={formatCompactXaf(stats.collectedToday)} />
      </div>

      {filter !== "all" && (
        <div className="flex items-center gap-2 text-xs text-on-surface-variant">
          {dict.agents.showingOnly} <Badge status={filter === "nearCeiling" ? "PENDING" : "SUSPENDED"} label={filter === "nearCeiling" ? dict.agents.nearCeilingLabel : dict.common.status.SUSPENDED} />
          <button
            onClick={() => setFilter("all")}
            className="text-primary hover:underline underline-offset-2 font-medium cursor-pointer transition-transform duration-150 ease-out hover:scale-105 active:scale-95"
          >
            {dict.agents.clear}
          </button>
        </div>
      )}

      {filtered.length === 0 ? (
        <div className="bg-surface-container-lowest rounded-lg border-2 border-outline-variant shadow-sm p-8 text-center text-on-surface-variant">
          {rows.length === 0 ? dict.agents.noAgentsYet : dict.agents.noAgentsMatch}
        </div>
      ) : (
        <div className="flex flex-col gap-10">
          {grouped.map(([branchName, branchRows]) => (
            <div key={branchName} className="flex flex-col gap-3">
              {grouped.length > 1 && (
                <button
                  onClick={() => setBranch(branchName)}
                  className="flex items-center gap-3 cursor-pointer group/branch text-left w-fit"
                  title={dict.agents.viewThisBranchOnly}
                >
                  <Icon name="location-on" className="size-6 text-primary shrink-0" />
                  <h2 className="text-h1 font-bold text-primary group-hover/branch:underline underline-offset-4">{branchName}</h2>
                  <span className="text-xs font-semibold text-on-surface-variant bg-surface-container-low px-2.5 py-1 rounded-[var(--radius-full)] border-2 border-outline-variant">
                    {t(branchRows.length === 1 ? dict.agents.branchGroupCountSingular : dict.agents.branchGroupCountPlural, {
                      count: branchRows.length,
                    })}
                  </span>
                </button>
              )}
              <div className="bg-surface-container-lowest rounded-lg border-2 border-outline-variant shadow-sm overflow-hidden flex flex-col">
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[800px]">
                  <thead>
                    <tr className="bg-surface-container-low border-b-2 border-outline-variant">
                      <th className="p-4 text-[13px] font-semibold text-on-surface-variant uppercase tracking-wider">{dict.agents.colName}</th>
                      <th className="p-4 text-[13px] font-semibold text-on-surface-variant uppercase tracking-wider">{dict.agents.colId}</th>
                      <th className="p-4 text-[13px] font-semibold text-on-surface-variant uppercase tracking-wider">{dict.agents.colStatus}</th>
                      <th className="p-4 text-[13px] font-semibold text-on-surface-variant uppercase tracking-wider w-64">{dict.agents.colCollectedVsLimit}</th>
                      <th className="p-4 text-[13px] font-semibold text-on-surface-variant uppercase tracking-wider text-right">{dict.agents.colActions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {branchRows.map((row) => (
                      <tr key={row.id} className={`transition-colors group ${row.nearLimit ? "bg-error-container/10 hover:bg-error-container/20" : "hover:bg-surface-bright"}`}>
                        <td className="p-4 min-h-[64px]">
                          <div className="flex items-center gap-3">
                            <div
                              className={`w-10 h-10 rounded-full flex items-center justify-center font-semibold text-[15px] ${
                                row.nearLimit ? "bg-error-container text-on-error-container" : "bg-primary-fixed text-on-primary-fixed"
                              }`}
                            >
                              {initials(row.fullName)}
                            </div>
                            <div>
                              <p className="font-semibold text-on-surface flex items-center gap-2">
                                {row.fullName}
                                {row.nearLimit && <Icon name="warning" filled className="text-error size-4" />}
                              </p>
                            </div>
                          </div>
                        </td>
                        <td className="p-4">
                          <p className="text-xs text-outline font-mono">{row.employeeCode}</p>
                        </td>
                        <td className="p-4">
                          <Badge status={row.status} />
                        </td>
                        <td className="p-4">
                          {row.collectedTodayXaf !== null && row.ceilingXaf !== null && row.pct !== null ? (
                            <div className="flex flex-col gap-1 w-full">
                              <div className="flex justify-between text-xs tabular-nums">
                                <span className={row.nearLimit ? "text-error font-semibold" : "text-on-surface"}>{row.collectedTodayXaf.toLocaleString()} XAF</span>
                                <span className={row.nearLimit ? "text-error font-semibold" : "text-on-surface-variant"}>{row.pct}%</span>
                              </div>
                              <div className="w-full bg-surface-variant rounded-full h-2 overflow-hidden">
                                <div
                                  className={`${row.nearLimit ? "bg-error" : "bg-primary"} h-2 rounded-full transition-[width] duration-500 ease-out`}
                                  style={{ width: `${Math.max(0, Math.min(100, row.pct))}%` }}
                                />
                              </div>
                            </div>
                          ) : (
                            "N/A"
                          )}
                        </td>
                        <td className="p-4 text-right">
                          <Link
                            href={`/agents/${row.id}`}
                            className="inline-flex items-center justify-center p-2 text-primary hover:bg-primary-fixed rounded transition-[background-color,transform] duration-150 ease-out hover:scale-110 active:scale-90"
                            title={dict.agents.viewProfile}
                          >
                            <Icon name="person" className="size-5" />
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function StatCard({
  label,
  value,
  hint,
  valueClass = "text-primary",
  active = false,
  onClick,
}: {
  label: string;
  value: string;
  hint?: string;
  valueClass?: string;
  active?: boolean;
  onClick?: () => void;
}) {
  const content = (
    <div
      className={`p-4 rounded-lg border-2 shadow-sm flex flex-col gap-2 text-left w-full h-full ${
        active ? "border-primary bg-primary-container/5" : "border-outline-variant bg-surface-container-lowest"
      } ${onClick ? "card-interactive cursor-pointer" : ""}`}
    >
      <span className="text-[13px] font-semibold text-on-surface-variant uppercase tracking-wider">{label}</span>
      <div className="flex items-end justify-between">
        <span className={`font-bold text-[32px] leading-[40px] tracking-tight tabular-nums ${valueClass}`}>{value}</span>
        {hint && <span className="text-xs text-on-surface-variant">{hint}</span>}
      </div>
    </div>
  );
  return onClick ? (
    <button
      onClick={onClick}
      className="text-left rounded-lg cursor-pointer focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      {content}
    </button>
  ) : (
    content
  );
}
