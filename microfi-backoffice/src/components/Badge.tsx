"use client";

import { useDictionary } from "@/lib/i18n/I18nProvider";

// DESIGN.md Components/Status Chips: 100px radius, highly saturated fixed-tone backgrounds with
// high-contrast text — dot + mint for positive states, error-container for negative, amber
// tertiary-fixed for pending, neutral surface-container otherwise.
export type BadgeStatus =
  | "SYNCED"
  | "PENDING"
  | "PENDING_CEILING"
  | "BLOCKED"
  | "ACTIVE"
  | "SUSPENDED"
  | "INACTIVE"
  | "EXPIRED"
  | "REVOKED"
  | "OPEN"
  | "CLOSED"
  | "RESOLVED"
  | "ACKNOWLEDGED";

const STATUS_STYLES: Record<BadgeStatus, { className: string; dot?: boolean }> = {
  SYNCED: { className: "bg-secondary-fixed text-on-secondary-fixed-variant", dot: true },
  ACTIVE: { className: "bg-secondary-fixed text-on-secondary-fixed-variant", dot: true },
  PENDING_CEILING: { className: "bg-tertiary-fixed text-on-tertiary-fixed-variant" },
  RESOLVED: { className: "bg-secondary-fixed text-on-secondary-fixed-variant", dot: true },
  OPEN: { className: "bg-tertiary-fixed text-on-tertiary-fixed-variant" },
  PENDING: { className: "bg-tertiary-fixed text-on-tertiary-fixed-variant" },
  ACKNOWLEDGED: { className: "bg-surface-container text-on-surface-variant" },
  BLOCKED: { className: "bg-error-container text-on-error-container" },
  SUSPENDED: { className: "bg-error-container text-on-error-container" },
  REVOKED: { className: "bg-error-container text-on-error-container" },
  CLOSED: { className: "bg-surface-container text-on-surface-variant" },
  INACTIVE: { className: "bg-surface-container text-on-surface-variant" },
  EXPIRED: { className: "bg-surface-container text-on-surface-variant" },
};

export function Badge({ status, label }: { status: BadgeStatus; label?: string }) {
  const dict = useDictionary();
  const style = STATUS_STYLES[status];
  return (
    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-[var(--radius-full)] text-xs font-semibold ${style.className}`}>
      {style.dot && <span className="w-1.5 h-1.5 rounded-full bg-secondary" />}
      {label ?? dict.common.status[status]}
    </span>
  );
}
