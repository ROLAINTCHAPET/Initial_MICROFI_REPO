"use client";

import Link from "next/link";
import { useMemo, useState, type ReactNode } from "react";
import { Badge } from "@/components/Badge";
import { Icon, type IconName } from "@/components/Icon";
import type { AgentStatus, AdminUserStatus } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { Dictionary } from "@/lib/i18n/dictionaries";

export type TeamMemberKind = "ADMIN" | "BRANCH_MANAGER" | "BRANCH_CASHIER" | "AGENT";

export interface TeamRow {
  id: string;
  href: string;
  login: string;
  role: TeamMemberKind;
  branchName: string | null;
  status: AgentStatus | AdminUserStatus;
}

function roleMeta(dict: Dictionary): Record<TeamMemberKind, { label: string; icon: IconName; chipClass: string }> {
  return {
    ADMIN: { label: dict.roles.ADMIN, icon: "shield-check", chipClass: "bg-primary-container text-white" },
    BRANCH_MANAGER: { label: dict.roles.BRANCH_MANAGER, icon: "agents", chipClass: "bg-secondary-fixed text-on-secondary-fixed-variant" },
    BRANCH_CASHIER: { label: dict.roles.BRANCH_CASHIER, icon: "account-balance-wallet", chipClass: "bg-tertiary-fixed text-on-tertiary-fixed-variant" },
    AGENT: { label: dict.team.directory.fieldAgent, icon: "person", chipClass: "bg-primary-fixed text-on-primary-fixed-variant" },
  };
}

function initials(login: string) {
  return login.slice(0, 2).toUpperCase();
}

export function TeamDirectory({ rows, actions }: { rows: TeamRow[]; actions?: ReactNode }) {
  const dict = useDictionary();
  const ROLE_META = useMemo(() => roleMeta(dict), [dict]);
  const [query, setQuery] = useState("");

  const stats = useMemo(
    () => ({
      total: rows.length,
      active: rows.filter((r) => r.status === "ACTIVE").length,
      suspended: rows.filter((r) => r.status === "SUSPENDED").length,
    }),
    [rows]
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((r) => `${r.login} ${ROLE_META[r.role].label} ${r.branchName ?? ""}`.toLowerCase().includes(q));
  }, [rows, query, ROLE_META]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="relative max-w-sm w-full">
          <Icon name="search" className="absolute left-3 top-1/2 -translate-y-1/2 size-5 text-outline pointer-events-none" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            type="text"
            placeholder={dict.team.directory.searchPlaceholder}
            className="w-full h-11 pl-10 pr-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm focus:outline-none focus:border-primary transition-colors"
          />
        </div>
        {actions}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard label={dict.team.directory.statTotalAccounts} value={stats.total.toLocaleString()} />
        <StatCard label={dict.team.directory.statActive} value={stats.active.toLocaleString()} valueClass="text-success-emerald" />
        <StatCard label={dict.team.directory.statSuspended} value={stats.suspended.toLocaleString()} valueClass={stats.suspended > 0 ? "text-danger-red" : undefined} />
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="divide-y divide-outline-variant">
          {filtered.map((row) => {
            const meta = ROLE_META[row.role];
            return (
              <Link
                key={row.id}
                href={row.href}
                className="card-interactive flex items-center justify-between gap-4 p-4 cursor-pointer"
              >
                <div className="flex items-center gap-4 min-w-0">
                  <div className="w-11 h-11 rounded-full bg-primary-container/10 border-2 border-primary-container/20 flex items-center justify-center text-primary text-sm font-bold shrink-0">
                    {initials(row.login)}
                  </div>
                  <div className="min-w-0">
                    <p className="font-semibold text-on-surface truncate">{row.login}</p>
                    <p className="text-xs text-text-slate mt-0.5">{row.branchName ?? dict.team.directory.allBranches}</p>
                  </div>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[var(--radius-full)] text-xs font-bold ${meta.chipClass}`}>
                    <Icon name={meta.icon} className="size-4" />
                    {meta.label}
                  </span>
                  <Badge status={row.status} />
                  <Icon name="chevron-right" className="size-5 text-outline" />
                </div>
              </Link>
            );
          })}
          {filtered.length === 0 && (
            <div className="p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
              <Icon name="info" className="size-6 text-outline-variant" />
              {rows.length === 0 ? dict.team.directory.noTeamMembersYet : dict.team.directory.noTeamMembersMatch}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, valueClass = "text-primary" }: { label: string; value: string; valueClass?: string }) {
  return (
    <div className="bg-surface-container-lowest p-4 rounded-lg border-2 border-outline-variant shadow-sm flex flex-col gap-2">
      <span className="text-[13px] font-semibold text-on-surface-variant uppercase tracking-wider">{label}</span>
      <span className={`font-bold text-[32px] leading-[40px] tracking-tight tabular-nums ${valueClass}`}>{value}</span>
    </div>
  );
}
