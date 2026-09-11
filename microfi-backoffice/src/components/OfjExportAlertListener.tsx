"use client";

import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { BranchResponse, OfjClosingExportAlertResponse } from "@/lib/types";

interface ToastEntry {
  id: string;
  branchLabel: string;
  postedCount: number;
}

// Mounted once in the dashboard layout, same placement/pattern as CollectionRejectionAlertListener
// — notices the scheduled closing-time OFJ export the moment it happens, whether or not every
// agent tapped "End My Day" first. No sound, no persistent badge (unlike SOS/rejections): there's
// nothing to review or act on here, just a one-off audit-trail confirmation, so a dismissing toast
// is the whole feature.
export function OfjExportAlertListener() {
  const dict = useDictionary();
  const [toasts, setToasts] = useState<ToastEntry[]>([]);
  const branchesRef = useRef<Map<string, BranchResponse>>(new Map());

  useEffect(() => {
    fetch("/api/branches")
      .then((r) => (r.ok ? r.json() : []))
      .then((branches: BranchResponse[]) => {
        branchesRef.current = new Map(branches.map((b) => [b.id, b]));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const source = new EventSource("/api/ofj-export-alerts/stream");
    source.onmessage = (message) => {
      let event: OfjClosingExportAlertResponse;
      try {
        event = JSON.parse(message.data);
      } catch {
        return;
      }
      const branch = branchesRef.current.get(event.branchId);
      const id = `${event.branchId}-${event.exportedAt}`;
      setToasts((current) => [...current, { id, branchLabel: branch ? branch.name : event.branchId, postedCount: event.postedCount }]);
      setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), 15000);
    };
    // EventSource reconnects on its own on a dropped connection — no custom retry logic needed.
    return () => source.close();
  }, []);

  return (
    <div className="fixed top-24 right-4 z-[2000] flex flex-col gap-2 w-80 max-w-[calc(100%-2rem)]">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className="flex items-center gap-3 px-4 py-3 rounded-[var(--radius-md)] bg-primary-container border-2 border-primary shadow-[var(--shadow-elevation-2)] panel-scale-in"
        >
          <Icon name="check-circle" filled className="size-5 text-on-primary-container shrink-0" />
          <span className="flex-1 text-sm font-semibold text-on-primary-container">
            {t(dict.ofjExportAlerts.completedToast, { branch: toast.branchLabel, count: toast.postedCount })}
          </span>
        </div>
      ))}
    </div>
  );
}
