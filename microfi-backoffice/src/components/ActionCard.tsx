"use client";

import type { ButtonHTMLAttributes } from "react";
import { Icon, type IconName } from "@/components/Icon";

interface ActionCardProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: IconName;
  title: string;
  description: string;
  danger?: boolean;
}

// The whole card is the trigger — no separate button — matching the reference design (Graphical
// Design/stitch_microfi_digital_cash_network). Used by every per-entity admin action (agent and
// team-member detail Administration tabs) so severity (danger vs neutral) reads instantly from
// color alone, without a dropdown or per-row border box.
export function ActionCard({ icon, title, description, danger = false, className = "", ...props }: ActionCardProps) {
  return (
    <button
      type="button"
      {...props}
      className={`flex items-start gap-4 p-5 rounded-[var(--radius-md)] border-2 transition-all group text-left cursor-pointer disabled:cursor-not-allowed disabled:opacity-60 ${
        danger
          ? "bg-error-container/20 border-error/30 hover:bg-error-container/40 hover:border-error"
          : "bg-surface-container-lowest border-outline-variant hover:border-primary hover:shadow-[var(--shadow-elevation-1)]"
      } ${className}`}
    >
      <div
        className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 transition-colors ${
          danger
            ? "bg-error/10 text-error group-hover:bg-error group-hover:text-on-error"
            : "bg-surface-container-high text-on-surface group-hover:bg-primary group-hover:text-on-primary"
        }`}
      >
        <Icon name={icon} className="size-5" />
      </div>
      <div className="flex-1 min-w-0">
        <h4 className={`font-semibold text-base mb-1 transition-colors ${danger ? "group-hover:text-error" : "text-on-surface group-hover:text-primary"}`}>{title}</h4>
        <p className="text-xs text-on-surface-variant">{description}</p>
      </div>
    </button>
  );
}
