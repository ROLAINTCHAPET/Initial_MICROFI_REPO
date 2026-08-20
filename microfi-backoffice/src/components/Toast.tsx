"use client";

import { useEffect } from "react";
import { Icon } from "./Icon";

// A brief, self-dismissing confirmation banner — "a little volatile dialog" for actions whose
// effect isn't otherwise visible in place (e.g. a bulk update to rows off-screen).
export function Toast({ message, onDismiss, durationMs = 2600 }: { message: string; onDismiss: () => void; durationMs?: number }) {
  useEffect(() => {
    const t = setTimeout(onDismiss, durationMs);
    return () => clearTimeout(t);
  }, [onDismiss, durationMs]);

  return (
    <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 panel-scale-in">
      <div className="flex items-center gap-2 px-5 py-3 rounded-[var(--radius-md)] bg-primary text-on-primary shadow-[var(--shadow-elevation-2)]">
        <Icon name="check-circle" filled className="size-5 text-secondary-fixed" />
        <span className="text-sm font-semibold">{message}</span>
      </div>
    </div>
  );
}
