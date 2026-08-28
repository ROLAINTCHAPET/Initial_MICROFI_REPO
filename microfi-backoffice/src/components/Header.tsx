"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { AdminRole } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import { Icon } from "./Icon";
import { LanguageSwitcher } from "./LanguageSwitcher";
import { usePageHeaderValue } from "./PageHeaderContext";
import { useMobileNav } from "./MobileNavContext";

function initials(name: string) {
  const parts = name.trim().split(/[.\s]+/).filter(Boolean);
  return ((parts[0]?.[0] ?? "") + (parts[1]?.[0] ?? parts[0]?.[1] ?? "")).toUpperCase();
}

export function Header({ login, role, unresolvedSosCount }: { login: string; role: AdminRole; unresolvedSosCount: number }) {
  const router = useRouter();
  const pageHeader = usePageHeaderValue();
  const [menuOpen, setMenuOpen] = useState(false);
  const { setOpen: setMobileNavOpen } = useMobileNav();
  const dict = useDictionary();
  const roleLabels: Record<AdminRole, string> = dict.roles;

  async function handleLogout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/login");
    router.refresh();
  }

  return (
    <header className="fixed top-0 right-0 left-0 md:left-64 h-20 bg-surface-container-lowest border-b-2 border-outline-variant flex items-center justify-between px-4 md:px-6 z-20 gap-4">
      <div className="flex items-center gap-3 min-w-0">
        <button
          onClick={() => setMobileNavOpen(true)}
          className="md:hidden shrink-0 p-2 -ml-2 text-on-surface-variant hover:bg-surface-container-low rounded-[var(--radius-sm)] transition-colors duration-150"
          aria-label={dict.header.openMenu}
        >
          <Icon name="menu" className="size-6" />
        </button>
        <div className="min-w-0">
          {pageHeader && (
            <>
              <h2 className="text-2xl font-bold text-on-surface truncate leading-tight">{pageHeader.title}</h2>
              {pageHeader.subtitle && <p className="text-xs text-text-slate truncate mt-0.5">{pageHeader.subtitle}</p>}
            </>
          )}
        </div>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <Link
          href="/sos"
          className="relative p-2 text-on-surface-variant hover:bg-surface-container-low rounded-[var(--radius-sm)] transition-[background-color,transform] duration-150 ease-out hover:scale-110 active:scale-90"
          title={unresolvedSosCount > 0 ? t(dict.header.sosTooltipUnresolved, { count: unresolvedSosCount }) : dict.header.sosTooltipNone}
        >
          <Icon name="bell" className="size-5" />
          {unresolvedSosCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 min-w-4 h-4 px-1 rounded-full bg-danger-red text-white text-[10px] font-bold flex items-center justify-center">
              {unresolvedSosCount}
            </span>
          )}
        </Link>
        <div className="hidden sm:block h-8 w-px bg-outline-variant mx-1 md:mx-2" />
        <LanguageSwitcher />
        <div className="hidden sm:block h-8 w-px bg-outline-variant mx-1 md:mx-2" />

        <div className="relative">
          <button
            onClick={() => setMenuOpen((o) => !o)}
            onBlur={() => setTimeout(() => setMenuOpen(false), 150)}
            className="flex items-center gap-3 pl-1.5 pr-1.5 sm:pr-3 py-1.5 rounded-[var(--radius-full)] cursor-pointer hover:bg-surface-container-low transition-[background-color,transform] duration-150 ease-out hover:scale-[1.02] active:scale-[0.98] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            <div className="w-9 h-9 rounded-full bg-primary-container text-white flex items-center justify-center text-xs font-bold shrink-0">
              {initials(login)}
            </div>
            <div className="hidden sm:block text-left">
              <p className="text-sm font-semibold text-on-surface leading-tight">{login}</p>
              <span className="inline-flex items-center px-1.5 py-0.5 rounded-[var(--radius-full)] bg-secondary-fixed/50 text-on-secondary-fixed-variant text-[10px] font-semibold uppercase tracking-wide leading-none">
                {roleLabels[role]}
              </span>
            </div>
            <Icon name="chevron-right" className={`size-4 text-outline transition-transform duration-200 ${menuOpen ? "rotate-90" : ""}`} />
          </button>

          {menuOpen && (
            <div className="panel-scale-in absolute right-0 top-full mt-2 w-44 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-2)] overflow-hidden">
              <div className="sm:hidden px-4 py-3 border-b-2 border-outline-variant">
                <p className="text-sm font-semibold text-on-surface leading-tight truncate">{login}</p>
                <span className="inline-flex items-center mt-1 px-1.5 py-0.5 rounded-[var(--radius-full)] bg-secondary-fixed/50 text-on-secondary-fixed-variant text-[10px] font-semibold uppercase tracking-wide leading-none">
                  {roleLabels[role]}
                </span>
              </div>
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-2 px-4 py-3 text-sm text-danger-red cursor-pointer hover:bg-error-container/40 transition-colors duration-150 active:bg-error-container/60"
              >
                <Icon name="sign-out" className="size-4" />
                {dict.header.signOut}
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
