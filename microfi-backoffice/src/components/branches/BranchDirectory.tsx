"use client";

import { useMemo, useState, type ReactNode } from "react";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import { BranchSettingsModal } from "./BranchSettingsModal";

export interface BranchRow {
  id: string;
  code: string;
  name: string;
  phone: string | null;
  timezone: string | null;
  openTime: string | null;
  closeTime: string | null;
  openTimeLocked: boolean;
  maxCashiers: number;
  requireImei: boolean;
  defaultCeilingPct: number;
  canEdit: boolean;
}

export function BranchDirectory({ branches, actions, locked = false }: { branches: BranchRow[]; actions?: ReactNode; locked?: boolean }) {
  const dict = useDictionary();
  const [query, setQuery] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return branches;
    return branches.filter((b) => `${b.name} ${b.code} ${b.timezone ?? ""}`.toLowerCase().includes(q));
  }, [branches, query]);

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4 flex-wrap">
        <div className="relative max-w-sm w-full">
          <Icon name="search" className="absolute left-3 top-1/2 -translate-y-1/2 size-5 text-outline pointer-events-none" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            type="text"
            placeholder={dict.branches.directory.filterPlaceholder}
            className="w-full h-11 pl-10 pr-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm focus:outline-none focus:border-primary transition-colors"
          />
        </div>
        {actions}
      </div>

      <div className="relative bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="px-5 py-4 border-b-2 border-outline-variant bg-surface-container-low flex items-center justify-between">
          <h2 className="text-h2 text-on-surface">{dict.branches.directory.title}</h2>
          {locked && (
            <span className="flex items-center gap-2 text-xs font-semibold text-on-surface-variant">
              <span aria-hidden className="h-3.5 w-3.5 rounded-full border-2 border-outline-variant border-t-primary animate-spin" />
              {dict.branches.directory.updatingSchedules}
            </span>
          )}
        </div>
        <div className={`divide-y divide-outline-variant transition-opacity duration-200 ${locked ? "opacity-50 pointer-events-none" : ""}`} aria-disabled={locked}>
          {filtered.map((branch) => (
            <BranchRowItem key={branch.id} branch={branch} />
          ))}
          {filtered.length === 0 && (
            <div className="p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
              <Icon name="info" className="size-6 text-outline-variant" />
              {branches.length === 0 ? dict.branches.directory.noBranchesYet : dict.branches.directory.noBranchesMatch}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// Rows are static at rest — schedule changes go through an explicit Edit action (modal),
// not always-on inline inputs, matching the rest of the app's edit patterns.
function BranchRowItem({ branch }: { branch: BranchRow }) {
  const dict = useDictionary();
  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4">
      <div className="flex items-center gap-4 min-w-0">
        <div className="w-11 h-11 rounded-[var(--radius-sm)] bg-secondary-fixed/40 border-2 border-secondary-fixed flex items-center justify-center shrink-0">
          <Icon name="branches" className="size-5 text-on-secondary-fixed-variant" />
        </div>
        <div className="min-w-0">
          <p className="font-semibold text-on-surface truncate">{branch.name}</p>
          <div className="flex items-center gap-2 mt-0.5">
            <span className="text-xs text-text-slate">{t(dict.branches.directory.idLabel, { code: branch.code })}</span>
            <span className="text-xs text-outline-variant">&middot;</span>
            <span className="text-xs text-text-slate">{branch.timezone ?? "—"}</span>
          </div>
        </div>
      </div>

      <div className="flex items-center gap-4 shrink-0">
        <div className="text-right">
          <p className="text-sm font-medium text-on-surface tabular-nums">
            {branch.openTime && branch.closeTime ? (
              `${branch.openTime.slice(0, 5)} – ${branch.closeTime.slice(0, 5)}`
            ) : (
              <span className="text-text-grey-disabled font-normal">{dict.branches.directory.hoursNotSet}</span>
            )}
          </p>
          <p className="text-xs text-text-slate tabular-nums">
            {branch.phone ?? <span className="text-text-grey-disabled">{dict.branches.directory.noContactNumber}</span>}
          </p>
        </div>
        {branch.canEdit ? (
          <BranchSettingsModal
            branchId={branch.id}
            branchName={branch.name}
            openTime={branch.openTime}
            closeTime={branch.closeTime}
            openTimeLocked={branch.openTimeLocked}
            phone={branch.phone}
            maxCashiers={branch.maxCashiers}
            requireImei={branch.requireImei}
            defaultCeilingPct={branch.defaultCeilingPct}
          />
        ) : (
          <span
            title={dict.branches.directory.editRestrictedTooltip}
            className="h-10 w-10 flex items-center justify-center rounded-full border-2 border-outline-variant text-outline bg-surface-container-low shrink-0"
          >
            <Icon name="lock" className="size-4" />
          </span>
        )}
      </div>
    </div>
  );
}
