"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon, type IconName } from "./Icon";
import type { AdminRole } from "@/lib/types";

const NAV_ITEMS: { href: string; label: string; icon: IconName }[] = [
  { href: "/", label: "Dashboard", icon: "dashboard" },
  { href: "/agents", label: "Agents", icon: "agents" },
  { href: "/team", label: "Team", icon: "shield-check" },
  { href: "/tracking", label: "Geolocation", icon: "location-on" },
  { href: "/ofj", label: "End of Day Oversight", icon: "reports" },
  { href: "/sos", label: "SOS Console", icon: "bell" },
];

// DESIGN.md: Primary Container navy sidebar, mint (secondary-fixed) active state with a left
// accent border. Nav items are the same across back-office roles — branch scoping happens
// server-side on the data itself, not by hiding whole screens. "Settings" and "Registrations"
// are the exceptions: both are ADMIN/BRANCH_MANAGER-only (Settings edits branch hours/contact/
// cashier cap/IMEI requirement; Registrations submits/reviews compliance dossiers — only ADMIN
// can approve/reject, and BRANCH_CASHIER never creates accounts either), so BRANCH_CASHIER
// doesn't see either.
export function Sidebar({ role }: { role: AdminRole }) {
  const pathname = usePathname();
  const navItems =
    role === "ADMIN" || role === "BRANCH_MANAGER"
      ? [
          ...NAV_ITEMS,
          { href: "/registrations", label: "Registrations", icon: "edit-note" as IconName },
          { href: "/settings", label: "Settings", icon: "settings" as IconName },
        ]
      : NAV_ITEMS;

  return (
    <aside className="hidden md:flex md:flex-col h-screen w-64 fixed left-0 top-0 bg-primary-container z-30 py-6">
      <div className="flex items-center gap-3 px-6 pb-8">
        <div className="w-9 h-9 rounded-[var(--radius-sm)] bg-primary flex items-center justify-center shrink-0">
          <Icon name="building" className="size-5 text-on-primary" />
        </div>
        <div>
          <p className="text-base font-bold text-white leading-tight">Microfi Admin</p>
          <p className="text-xs text-on-primary-container leading-tight">Regional Operations</p>
        </div>
      </div>

      <div className="px-4 mb-4">
        <Link
          href="/cashier"
          className="w-full h-12 bg-secondary text-on-secondary font-semibold text-sm rounded-[var(--radius-sm)] flex items-center justify-center gap-2 hover:bg-secondary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
        >
          <Icon name="plus" className="size-5" />
          Collect Cash
        </Link>
      </div>

      <ul className="flex flex-col gap-1 px-2">
        {navItems.map((item) => {
          const active = item.href === "/" ? pathname === "/" : pathname?.startsWith(item.href);
          return (
            <li key={item.href}>
              <Link
                href={item.href}
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
    </aside>
  );
}
