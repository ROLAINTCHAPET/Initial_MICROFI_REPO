"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect } from "react";
import { Icon, type IconName } from "./Icon";
import type { AdminRole } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { Dictionary } from "@/lib/i18n/dictionaries";
import { useMobileNav } from "./MobileNavContext";

function navItems(dict: Dictionary): { href: string; label: string; icon: IconName }[] {
  return [
    { href: "/", label: dict.sidebar.dashboard, icon: "dashboard" },
    { href: "/agents", label: dict.sidebar.agents, icon: "agents" },
    { href: "/team", label: dict.sidebar.team, icon: "shield-check" },
    { href: "/tracking", label: dict.sidebar.geolocation, icon: "location-on" },
    { href: "/ofj", label: dict.sidebar.endOfDayOversight, icon: "reports" },
    { href: "/sos", label: dict.sidebar.sosConsole, icon: "bell" },
  ];
}

// DESIGN.md: Primary Container navy sidebar, mint (secondary-fixed) active state with a left
// accent border. Nav items are the same across back-office roles — branch scoping happens
// server-side on the data itself, not by hiding whole screens. "Settings" and "Registrations"
// are the exceptions: both are ADMIN/BRANCH_MANAGER-only (Settings edits branch hours/contact/
// cashier cap/IMEI requirement; Registrations submits/reviews compliance dossiers — only ADMIN
// can approve/reject, and BRANCH_CASHIER never creates accounts either), so BRANCH_CASHIER
// doesn't see either.
type NavItem = { href: string; label: string; icon: IconName };

function SidebarContent({ items, pathname, dict, onNavigate }: { items: NavItem[]; pathname: string | null; dict: Dictionary; onNavigate?: () => void }) {
  return (
    <>
      <div className="flex items-center gap-3 px-6 pb-8">
        <div className="w-9 h-9 rounded-[var(--radius-sm)] bg-primary flex items-center justify-center shrink-0">
          <Icon name="building" className="size-5 text-on-primary" />
        </div>
        <div>
          <p className="text-base font-bold text-white leading-tight">{dict.sidebar.brandName}</p>
          <p className="text-xs text-on-primary-container leading-tight">{dict.sidebar.brandSubtitle}</p>
        </div>
      </div>

      <div className="px-4 mb-4">
        <Link
          href="/cashier"
          onClick={onNavigate}
          className="w-full h-12 bg-secondary text-on-secondary font-semibold text-sm rounded-[var(--radius-sm)] flex items-center justify-center gap-2 hover:bg-secondary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
        >
          <Icon name="plus" className="size-5" />
          {dict.sidebar.collectCash}
        </Link>
      </div>

      <ul className="flex flex-col gap-1 px-2">
        {items.map((item) => {
          const active = item.href === "/" ? pathname === "/" : pathname?.startsWith(item.href);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                onClick={onNavigate}
                className={`flex items-center gap-3 px-4 py-3 rounded-[var(--radius-sm)] text-sm font-medium transition-[background-color,color,transform] duration-150 ease-out hover:scale-[1.02] active:scale-[0.98] border-l-4 ${
                  active
                    ? "bg-white/10 text-secondary-fixed border-secondary-fixed"
                    : "border-transparent text-on-primary-container hover:bg-white/5 hover:text-secondary-fixed-dim"
                }`}
              >
                <Icon name={item.icon} className="size-5" />
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </>
  );
}

export function Sidebar({ role }: { role: AdminRole }) {
  const pathname = usePathname();
  const dict = useDictionary();
  const { open, setOpen } = useMobileNav();
  const items =
    role === "ADMIN" || role === "BRANCH_MANAGER"
      ? [
          ...navItems(dict),
          { href: "/registrations", label: dict.sidebar.registrations, icon: "edit-note" as IconName },
          { href: "/settings", label: dict.sidebar.settings, icon: "settings" as IconName },
        ]
      : navItems(dict);

  // Closes the drawer whenever the route actually changes — a safety net alongside each link's
  // own onClick, so back/forward navigation or a link that doesn't fire onClick still closes it.
  useEffect(() => {
    setOpen(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  return (
    <>
      <aside className="hidden md:flex md:flex-col h-screen w-64 fixed left-0 top-0 bg-primary-container z-30 py-6">
        <SidebarContent items={items} pathname={pathname} dict={dict} />
      </aside>

      {open && (
        <div className="md:hidden fixed inset-0 z-40 flex">
          <div className="overlay-fade-in fixed inset-0 bg-primary/60" onClick={() => setOpen(false)} />
          <aside className="drawer-slide-in relative flex flex-col h-screen w-64 max-w-[80vw] bg-primary-container py-6 overflow-y-auto">
            <button
              onClick={() => setOpen(false)}
              aria-label={dict.common.close}
              className="absolute top-4 right-4 p-1.5 rounded-full text-on-primary-container hover:bg-white/10 transition-colors duration-150"
            >
              <Icon name="close" className="size-5" />
            </button>
            <SidebarContent items={items} pathname={pathname} dict={dict} onNavigate={() => setOpen(false)} />
          </aside>
        </div>
      )}
    </>
  );
}
