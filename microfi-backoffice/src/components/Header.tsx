"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { AdminRole } from "@/lib/types";
import { Icon } from "./Icon";
import { usePageHeaderValue } from "./PageHeaderContext";

const ROLE_LABELS: Record<AdminRole, string> = {
  ADMIN: "Administrator",
  BRANCH_MANAGER: "Branch Manager",
  BRANCH_CASHIER: "Branch Cashier",
};

function initials(name: string) {
  const parts = name.trim().split(/[.\s]+/).filter(Boolean);
  return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? parts[0]?.[1] ?? "")).toUpperCase();
}

export function Header({ login, role, unresolvedSosCount }: { login: string; role: AdminRole; unresolvedSosCount: number }) {
  const router = useRouter();
  const pageHeader = usePageHeaderValue();
  const [menuOpen, setMenuOpen] = useState(false);

  async function handleLogout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
    router.refresh();
  }

  return (
    <header className="fixed top-0 right-0 left-64 h-20 bg-surface-container-lowest border-b-2 border-outline-variant flex items-center justify-between px-6 z-20 gap-4">
      <div className="min-w-0">
        {pageHeader && (
          <>
            <h2 className="text-2xl font-bold text-on-surface truncate leading-tight">{pageHeader.title}</h2>
            {pageHeader.subtitle && <p className="text-xs text-text-slate truncate mt-0.5">{pageHeader.subtitle}</p>}
          </>
        )}
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <Link
          href="/sos"
          className="relative p-2 text-on-surface-variant hover:bg-surface-container-low rounded-[var(--radius-sm)] transition-[background-color,transform] duration-150 ease-out hover:scale-110 active:scale-90"
          title={unresolvedSosCount > 0 ? `${unresolvedSosCount} unresolved SOS alert(s)` : "SOS Console"}
        >
          <Icon name="bell" className="size-5" />
          {unresolvedSosCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-danger-red text-white text-[10px] font-bold flex items-center justify-center">
              {unresolvedSosCount}
            </span>
          )}
        </Link>
        <div className="h-8 w-px bg-outline-variant mx-2" />

        <div className="relative">
          <button
            onClick={() => setMenuOpen((o) => !o)}
            onBlur={() => setTimeout(() => setMenuOpen(false), 150)}
            className="flex items-center gap-3 pl-1.5 pr-3 py-1.5 rounded-[var(--radius-full)] cursor-pointer hover:bg-surface-container-low transition-[background-color,transform] duration-150 ease-out hover:scale-[1.02] active:scale-[0.98] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <div className="w-9 h-9 rounded-full bg-primary-container text-white flex items-center justify-center text-xs font-bold shrink-0">
              {initials(login)}
            </div>
            <div className="text-left">
              <p className="text-sm font-semibold text-on-surface leading-tight">{login}</p>
              <span className="inline-flex items-center px-1.5 py-0.5 rounded-[var(--radius-full)] bg-secondary-fixed/50 text-on-secondary-fixed-variant text-[10px] font-semibold uppercase tracking-wide leading-none">
                {ROLE_LABELS[role]}
              </span>
            </div>
            <Icon name="chevron-right" className={`size-4 text-outline transition-transform duration-200 ${menuOpen ? "rotate-90" : ""}`} />
          </button>

          {menuOpen && (
            <div className="panel-scale-in absolute right-0 top-full mt-2 w-44 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-2)] overflow-hidden">
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-2 px-4 py-3 text-sm text-danger-red cursor-pointer hover:bg-error-container/40 transition-colors duration-150 active:bg-error-container/60"
              >
                <Icon name="sign-out" className="size-4" />
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
