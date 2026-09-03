"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Badge } from "@/components/Badge";
import type { AgentStatus, GeofenceAlertResponse, GeofenceResponse, RouteResponse } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { Dictionary } from "@/lib/i18n/dictionaries";

const TrackingMap = dynamic(() => import("./TrackingMap").then((m) => m.TrackingMap), {
  ssr: false,
  loading: () => <MapLoadingFallback />,
});

// Rendered by next/dynamic while the map chunk loads, inside the same React tree as the rest of
// the page (and therefore inside I18nProvider) — so it's a real function component with its own
// hook call, not a plain callback.
function MapLoadingFallback() {
  const dict = useDictionary();
  return (
    <div className="h-full w-full flex items-center justify-center text-sm text-on-surface-variant">
      {dict.tracking.workspace.loadingMap}
    </div>
  );
}

export interface TrackingAgent {
  id: string;
  fullName: string;
  employeeCode: string;
  phone: string;
  branchName: string;
  status: AgentStatus;
  balanceXaf: number | null;
  lastPingAt: string | null;
  lastCollectionAt: string | null;
  hasActiveAlert: boolean;
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function timeAgo(iso: string, dict: Dictionary) {
  const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return dict.tracking.workspace.justNow;
  if (mins < 60) return t(dict.tracking.workspace.minutesAgo, { mins });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t(dict.tracking.workspace.hoursAgo, { hours });
  return t(dict.tracking.workspace.daysAgo, { days: Math.floor(hours / 24) });
}

function formatDuration(fromIso: string, nowMs: number) {
  const totalSeconds = Math.max(0, Math.floor((nowMs - new Date(fromIso).getTime()) / 1000));
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

export function TrackingWorkspace({ agents }: { agents: TrackingAgent[] }) {
  const dict = useDictionary();
  const [selectedId, setSelectedId] = useState<string | null>(agents.find((a) => a.hasActiveAlert)?.id ?? agents[0]?.id ?? null);
  const [date, setDate] = useState(todayIso());
  const [route, setRoute] = useState<RouteResponse | null>(null);
  const [geofence, setGeofence] = useState<GeofenceResponse | null>(null);
  const [alerts, setAlerts] = useState<GeofenceAlertResponse[]>([]);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);

  const [nowMs, setNowMs] = useState(() => Date.now());

  const selectedAgent = agents.find((a) => a.id === selectedId) ?? null;
  const requestKey = selectedId ? `${selectedId}:${date}` : null;
  const loading = requestKey !== null && loadedKey !== requestKey;

  useEffect(() => {
    if (!selectedId) return;
    let cancelled = false;
    const key = `${selectedId}:${date}`;

    // Polled, not one-shot: a geofence breach can start or resolve while an admin is already
    // looking at this agent, and the bottom-right banner is driven entirely by `alerts` here —
    // the page-level AutoRefresh only refreshes the sidebar list's `hasActiveAlert` flags via new
    // server props, it doesn't touch this component's own client-side fetches.
    function load() {
      Promise.all([
        fetch(`/api/agents/${selectedId}/route?date=${date}`).then((r) => (r.ok ? r.json() : null)),
        fetch(`/api/agents/${selectedId}/geofence`).then((r) => (r.ok ? r.json() : null)),
        fetch(`/api/agents/${selectedId}/geofence-alerts`).then((r) => (r.ok ? r.json() : [])),
      ]).then(([routeData, geofenceData, alertsData]) => {
        if (cancelled) return;
        setRoute(routeData);
        setGeofence(geofenceData);
        setAlerts(Array.isArray(alertsData) ? alertsData : []);
        setLoadedKey(key);
      });
    }
    load();
    const interval = setInterval(load, 10000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [selectedId, date]);

  useEffect(() => {
    const interval = setInterval(() => setNowMs(Date.now()), 30000);
    return () => clearInterval(interval);
  }, []);

  const activeAlert = alerts.find((a) => a.active) ?? null;

  if (agents.length === 0) {
    return (
      <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
        <Icon name="location-on" className="size-6 text-outline-variant" />
        {dict.tracking.workspace.noAgents}
      </div>
    );
  }

  return (
    <div className="relative">
      <div className="relative rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden bg-surface-container-low h-[calc(100vh-220px)] min-h-[480px]">
        <TrackingMap
          agentId={selectedId ?? undefined}
          points={route?.points ?? []}
          transactions={route?.transactions ?? []}
          geofence={geofence?.vertices ?? null}
          editingVertices={null}
        />

        {/* Floating over the map, matching the design reference — not a layout column, so it never
            fights the map for space and can't push it out of view. */}
        <div className="absolute top-3 left-3 z-[1000] w-80 max-w-[calc(100%-1.5rem)] max-h-[min(60vh,520px)] flex flex-col bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] overflow-hidden">
        <div className="px-4 py-3 border-b-2 border-outline-variant bg-surface-container-low flex items-center justify-between shrink-0">
          <h2 className="text-h2 text-on-surface">{dict.tracking.workspace.activeFieldUnits}</h2>
          <span className="inline-flex items-center px-2 py-0.5 rounded-[var(--radius-full)] bg-secondary-fixed text-on-secondary-fixed-variant text-xs font-bold">
            {t(dict.tracking.workspace.totalCount, { count: agents.length })}
          </span>
        </div>
        <div className="overflow-y-auto divide-y divide-outline-variant">
          {agents.map((agent) => {
            const active = agent.id === selectedId;
            return (
              <button
                key={agent.id}
                onClick={() => setSelectedId(agent.id)}
                className={`w-full text-left p-3 flex flex-col gap-1 cursor-pointer transition-colors border-l-4 ${
                  active ? "bg-primary-container/10 border-l-primary" : "border-l-transparent hover:bg-surface-container-low"
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <p className="font-semibold text-sm text-on-surface truncate">{agent.fullName}</p>
                  {agent.lastPingAt && <span className="text-xs text-on-surface-variant shrink-0">{timeAgo(agent.lastPingAt, dict)}</span>}
                </div>
                <p className="text-xs text-on-surface-variant">
                  {agent.employeeCode} &middot; {agent.branchName}
                </p>
                <div className="flex items-center gap-2 mt-0.5">
                  {agent.status === "SUSPENDED" && <Badge status="SUSPENDED" />}
                  {agent.hasActiveAlert ? (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-error">
                      <Icon name="warning" filled className="size-3.5" />
                      {dict.tracking.workspace.outOfZone}
                    </span>
                  ) : agent.lastPingAt ? (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-secondary">
                      <Icon name="check-circle" className="size-3.5" />
                      {dict.tracking.workspace.inZone}
                    </span>
                  ) : (
                    <span className="text-xs text-text-grey-disabled">{dict.tracking.workspace.noPingsToday}</span>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {activeAlert && selectedAgent && (
        <div className="absolute bottom-3 right-3 left-3 sm:left-auto z-[1000] max-w-sm bg-error-container border-2 border-error rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-2)] p-4 flex flex-col gap-3">
          <div className="flex items-center gap-2 text-on-error-container font-bold">
            <Icon name="warning" filled className="size-5" />
            {dict.tracking.workspace.geofenceBreach}
          </div>
          <p className="text-sm text-on-error-container">
            <span className="font-semibold">{selectedAgent.fullName}</span>{" "}
            {t(dict.tracking.workspace.outsideZoneSince, { time: new Date(activeAlert.firstDetectedOutsideAt).toLocaleTimeString() })}
          </p>
          <div className="flex items-center justify-between text-sm">
            <span className="text-on-error-container/80">{dict.tracking.workspace.liabilityAtRisk}</span>
            <span className="font-bold tabular-nums text-on-error-container">
              {selectedAgent.balanceXaf !== null ? `${selectedAgent.balanceXaf.toLocaleString()} XAF` : "N/A"}
            </span>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-on-error-container/80">{dict.tracking.workspace.breachDuration}</span>
            <span className="font-bold tabular-nums text-on-error-container">{formatDuration(activeAlert.firstDetectedOutsideAt, nowMs)}</span>
          </div>
          <a
            href={`tel:${selectedAgent.phone}`}
            className="h-10 px-4 rounded-[var(--radius-sm)] bg-danger-red text-white text-sm font-semibold cursor-pointer flex items-center justify-center gap-2 transition-transform duration-150 ease-out hover:scale-[1.02] active:scale-95"
          >
            {t(dict.tracking.workspace.callAgent, { phone: selectedAgent.phone })}
          </a>
        </div>
      )}
      </div>

      <div className="relative sm:absolute sm:top-3 sm:right-3 mt-3 sm:mt-0 z-[1000] flex flex-col items-stretch sm:items-end gap-3 w-full sm:w-auto">
        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] px-4 py-3 flex items-center gap-3 flex-wrap">
          {selectedAgent && (
            <div className="text-right">
              <p className="font-bold text-sm text-primary leading-tight">{selectedAgent.fullName}</p>
              <p className="text-xs text-on-surface-variant">{selectedAgent.employeeCode}</p>
            </div>
          )}
          <label className="flex items-center gap-2 text-xs font-semibold text-on-surface-variant">
            {dict.tracking.workspace.dateLabel}
            <input
              type="date"
              value={date}
              max={todayIso()}
              onChange={(e) => setDate(e.target.value)}
              className="h-9 px-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm cursor-pointer focus:outline-none focus:border-primary"
            />
          </label>
          {loading && <span className="h-4 w-4 rounded-full border-2 border-outline-variant border-t-primary animate-spin" aria-label={dict.common.loading} />}
        </div>
      </div>
    </div>
  );
}
