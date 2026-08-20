"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

interface HeaderTitle {
  title: string;
  subtitle?: string;
}

interface Ctx {
  value: HeaderTitle | null;
  setValue: (v: HeaderTitle | null) => void;
}

const PageHeaderContext = createContext<Ctx | null>(null);

export function PageHeaderProvider({ children }: { children: ReactNode }) {
  const [value, setValue] = useState<HeaderTitle | null>(null);
  return <PageHeaderContext.Provider value={{ value, setValue }}>{children}</PageHeaderContext.Provider>;
}

export function usePageHeaderValue(): HeaderTitle | null {
  return useContext(PageHeaderContext)?.value ?? null;
}

// Server Component pages render this to set the title/subtitle the Header displays —
// mirrors the design mockups, which show the current section's name in the top bar
// (e.g. "Agent Management") rather than a static brand string.
export function PageHeader({ title, subtitle }: HeaderTitle) {
  const ctx = useContext(PageHeaderContext);
  useEffect(() => {
    ctx?.setValue({ title, subtitle });
    return () => ctx?.setValue(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title, subtitle]);
  return null;
}
