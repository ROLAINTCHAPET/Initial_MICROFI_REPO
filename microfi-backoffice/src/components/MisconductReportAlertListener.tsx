"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { AgentResponse, AgentMisconductReportResponse } from "@/lib/types";

interface ToastEntry {
  id: string;
  agentLabel: string;
}

// Mounted once in the dashboard layout, same placement/pattern as CollectionRejectionAlertListener
// — noticing a new misconduct report while working on something else entirely. No sound (unlike
// SOS): this isn't an emergency, just a routine review item, so a toast is enough.
export function MisconductReportAlertListener() {
  const dict = useDictionary();
  const [toasts, setToasts] = useState<ToastEntry[]>([]);
  const agentsRef = useRef<Map<string, AgentResponse>>(new Map());

  useEffect(() => {
    fetch("/api/agents")
      .then((r) => (r.ok ? r.json() : []))
      .then((agents: AgentResponse[]) => {
        agentsRef.current = new Map(agents.map((a) => [a.id, a]));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const source = new EventSource("/api/misconduct-reports/stream");
    source.onmessage = (message) => {
      let event: AgentMisconductReportResponse;
      try {
        event = JSON.parse(message.data);
      } catch {
        return;
      }
      const agent = agentsRef.current.get(event.agentId);
      setToasts((current) => [...current, { id: event.id, agentLabel: agent ? agent.fullName : event.agentId }]);
      setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== event.id)), 15000);
    };
    // EventSource reconnects on its own on a dropped connection — no custom retry logic needed.
    return () => source.close();
  }, []);

  return (
    <div className="fixed top-24 right-4 z-[2000] flex flex-col gap-2 w-80 max-w-[calc(100%-2rem)]">
      {toasts.map((toast) => (
        <Link
          key={toast.id}
          href="/misconduct-reports"
          className="flex items-center gap-3 px-4 py-3 rounded-[var(--radius-md)] bg-secondary-container border-2 border-secondary shadow-[var(--shadow-elevation-2)] panel-scale-in hover:scale-[1.01] transition-transform duration-150 ease-out"
        >
          <Icon name="warning" filled className="size-5 text-on-secondary-container shrink-0" />
          <span className="flex-1 text-sm font-semibold text-on-secondary-container">
            {t(dict.misconductReports.newReportToast, { agent: toast.agentLabel })}
          </span>
          <span className="text-xs font-bold text-on-secondary-container underline underline-offset-2 shrink-0">
            {dict.sos.viewAction}
          </span>
        </Link>
      ))}
    </div>
  );
}
