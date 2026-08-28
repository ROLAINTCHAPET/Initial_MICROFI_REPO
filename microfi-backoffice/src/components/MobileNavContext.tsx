"use client";

import { createContext, useContext, useState, type ReactNode } from "react";

interface Ctx {
  open: boolean;
  setOpen: (v: boolean) => void;
}

const MobileNavContext = createContext<Ctx | null>(null);

export function MobileNavProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return <MobileNavContext.Provider value={{ open, setOpen }}>{children}</MobileNavContext.Provider>;
}

// Shares the mobile drawer's open/closed state between Header (which owns the hamburger
// trigger) and Sidebar (which renders the drawer) — same cross-component-without-prop-drilling
// shape as PageHeaderContext, just for the opposite direction (Header -> Sidebar instead of
// page -> Header).
export function useMobileNav(): Ctx {
  const ctx = useContext(MobileNavContext);
  if (!ctx) {
    throw new Error("useMobileNav must be used within MobileNavProvider");
  }
  return ctx;
}
