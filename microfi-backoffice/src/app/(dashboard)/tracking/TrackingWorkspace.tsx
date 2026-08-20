"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { Badge } from "@/components/Badge";
import type { AgentStatus, GeofenceAlertResponse, GeofenceResponse, GeofenceVertex, RouteResponse } from "@/lib/types";

const TrackingMap = dynamic(() => import("./TrackingMap").then((m) => m.TrackingMap), {
  ssr: false,
  loading: () => (
    <div className="h-full w-full flex items-center justify-center text-sm text-on-surface-variant">Loading map…</div>
  ),
});

export interface TrackingAgent {
  id: string;
  fullName: string;
  employeeCode: string;
  phone: string;
  branchName: string;
  status: AgentStatus;
  balanceXaf: number | null;
  lastPingAt: string | null;
  hasActiveAlert: boolean;
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function timeAgo(iso: string) {
  const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return "Just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

function formatDuration(fromIso: string, nowMs: number) {
  const totalSeconds = Math.max(0, Math.floor((nowMs - new Date(fromIso).getTime()) / 1000));
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

export function TrackingWorkspace({ agents, canEditGeofence }: { agents: TrackingAgent[]; canEditGeofence: boolean }) {
  const [selectedId, setSelectedId] = useState<string | null>(agents.find((a) => a.hasActiveAlert)?.id ?? agents[0]?.id ?? null);
  const [date, setDate] = useState(todayIso());
  const [route, setRoute] = useState<RouteResponse | null>(null);
  const [geofence, setGeofence] = useState<GeofenceResponse | null>(null);
  const [alerts, setAlerts] = useState<GeofenceAlertResponse[]>([]);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);

  const [editing, setEditing] = useState(false);
  const [draftVertices, setDraftVertices] = useState<GeofenceVertex[]>([]);
  const [savingGeofence, setSavingGeofence] = useState(false);
  const [geofenceError, setGeofenceError] = useState<string | null>(null);

  const [nowMs, setNowMs] = useState(() => Date.now());

  const selectedAgent = agents.find((a) => a.id === selectedId) ?? null;
  const requestKey = selectedId ? `${selectedId}:${date}` : null;
  const loading = requestKey !== null && loadedKey !== requestKey;

  useEffect(() => {
    if (!selectedId) return;
    let cancelled = false;
    const key = `${selectedId}:${date}`;
    Promise.all([
      fetch(`/api/agents/${selectedId}/route?date=${date}`).then((r) => (r.ok ? r.json() : null)),
      fetch(`/api/agents/${selectedId}/geofence`).then((r) => (r.ok ? r.json() : null)),
      fetch(`/api/agents/${selectedId}/geofence-alerts`).then((r) => (r.ok ? r.json() : [])),
    ]).then(([routeData, geofenceData, alertsData]) => {
      if (cancelled) return;
      setRoute(routeData);
      setGeofence(geofenceData);
      setAlerts(Array.isArray(alertsData) ? alertsData : []);
      setEditing(false);
      setDraftVertices([]);
      setGeofenceError(null);
      setLoadedKey(key);
    });
    return () => {
      cancelled = true;
    };
  }, [selectedId, date]);

  useEffect(() => {
    const interval = setInterval(() => setNowMs(Date.now()), 30000);
    return () => clearInterval(interval);
  }, []);

  const activeAlert = alerts.find((a) => a.active) ?? null;

  function handleMapClick(lat: number, lon: number) {
    if (!editing) return;
    setDraftVertices((v) => [...v, { lat: Math.round(lat * 1e5) / 1e5, lon: Math.round(lon * 1e5) / 1e5 }]);
  }

  function startEditing() {
    // Starts from a clean slate rather than the existing polygon's vertices — there's no
    // vertex-dragging support, so pre-filling would let new clicks silently mix in with the old
    // points into one malformed shape. The old geofence stays visible underneath (teal) as a
    // reference while the new one (red, dashed) is drawn on top; Save replaces it outright.
    setEditing(true);
    setDraftVertices([]);
    setGeofenceError(null);
  }

  function cancelEditing() {
    setEditing(false);
    setDraftVertices([]);
    setGeofenceError(null);
  }

  async function saveGeofence() {
    if (!selectedId) return;
    if (draftVertices.length < 3) {
      setGeofenceError("A geofence needs at least 3 points — click the map to add more.");
      return;
    }
    setSavingGeofence(true);
    setGeofenceError(null);
    try {
      const res = await fetch(`/api/agents/${selectedId}/geofence`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ vertices: draftVertices }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setGeofenceError(body?.message ?? "Failed to save geofence");
        return;
      }
      const saved = await res.json();
      setGeofence(saved);
      setEditing(false);
      setDraftVertices([]);
    } catch {
      setGeofenceError("Unable to reach the server");
    } finally {
      setSavingGeofence(false);
    }
  }

  if (agents.length === 0) {
    return (
      <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
        <Icon name="location-on" className="size-6 text-outline-variant" />
        No field agents to track.
      </div>
    );
  }

  return (
    <div className="relative rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden bg-surface-container-low h-[calc(100vh-220px)] min-h-[480px]">
      <TrackingMap
        points={route?.points ?? []}
        transactions={route?.transactions ?? []}
        geofence={geofence?.vertices ?? null}
        editingVertices={editing ? draftVertices : null}
        onMapClick={editing ? handleMapClick : undefined}
      />

      {/* Floating over the map, matching the design reference — not a layout column, so it never
          fights the map for space and can't push it out of view. */}
      <div className="absolute top-3 left-3 z-[1000] w-80 max-w-[calc(100%-1.5rem)] max-h-[min(60vh,520px)] flex flex-col bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] overflow-hidden">
        <div className="px-4 py-3 border-b-2 border-outline-variant bg-surface-container-low flex items-center justify-between shrink-0">
          <h2 className="text-h2 text-on-surface">Active Field Units</h2>
          <span className="inline-flex items-center px-2 py-0.5 rounded-[var(--radius-full)] bg-secondary-fixed text-on-secondary-fixed-variant text-xs font-bold">
            {agents.length} total
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
                  {agent.lastPingAt && <span className="text-xs text-on-surface-variant shrink-0">{timeAgo(agent.lastPingAt)}</span>}
                </div>
                <p className="text-xs text-on-surface-variant">
                  {agent.employeeCode} &middot; {agent.branchName}
                </p>
                <div className="flex items-center gap-2 mt-0.5">
                  {agent.status === "SUSPENDED" && <Badge status="SUSPENDED" />}
                  {agent.hasActiveAlert ? (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-error">
                      <Icon name="warning" filled className="size-3.5" />
                      Out of zone
                    </span>
                  ) : agent.lastPingAt ? (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold text-secondary">
                      <Icon name="check-circle" className="size-3.5" />
                      In zone
                    </span>
                  ) : (
                    <span className="text-xs text-text-grey-disabled">No pings today</span>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      <div className="absolute top-3 right-3 z-[1000] flex flex-col items-end gap-3">
        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] px-4 py-3 flex items-center gap-3 flex-wrap">
          {selectedAgent && (
            <div className="text-right">
              <p className="font-bold text-sm text-primary leading-tight">{selectedAgent.fullName}</p>
              <p className="text-xs text-on-surface-variant">{selectedAgent.employeeCode}</p>
            </div>
          )}
          <label className="flex items-center gap-2 text-xs font-semibold text-on-surface-variant">
            Date
            <input
              type="date"
              value={date}
              max={todayIso()}
              onChange={(e) => setDate(e.target.value)}
              className="h-9 px-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm cursor-pointer focus:outline-none focus:border-primary"
            />
          </label>
          {loading && <span className="h-4 w-4 rounded-full border-2 border-outline-variant border-t-primary animate-spin" aria-label="Loading" />}
        </div>

        {canEditGeofence && selectedAgent && (
          <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] px-4 py-3 flex flex-col gap-2">
            {!editing ? (
              <button
                onClick={startEditing}
                className="h-9 px-4 rounded-[var(--radius-sm)] bg-primary text-on-primary text-sm font-semibold cursor-pointer flex items-center gap-2 transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95"
              >
                <Icon name="edit-note" className="size-4" />
                {geofence ? "Edit Geofence" : "Set Geofence"}
              </button>
            ) : (
              <>
                <p className="text-xs text-on-surface-variant">Click the map to place vertices ({draftVertices.length} placed, min. 3)</p>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setDraftVertices((v) => v.slice(0, -1))}
                    disabled={draftVertices.length === 0}
                    className="h-9 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant text-primary text-xs font-semibold cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed hover:bg-surface-container-low transition-colors"
                  >
                    Undo point
                  </button>
                  <button
                    onClick={cancelEditing}
                    className="h-9 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant text-primary text-xs font-semibold cursor-pointer hover:bg-surface-container-low transition-colors"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={saveGeofence}
                    disabled={savingGeofence}
                    className="h-9 px-3 rounded-[var(--radius-sm)] bg-primary text-on-primary text-xs font-semibold cursor-pointer disabled:opacity-60 transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95"
                  >
                    {savingGeofence ? "Saving…" : "Save Geofence"}
                  </button>
                </div>
                {geofenceError && <p role="alert" className="text-xs text-danger-red">{geofenceError}</p>}
              </>
            )}
          </div>
        )}
      </div>

      {activeAlert && selectedAgent && (
        <div className="absolute bottom-3 right-3 left-3 sm:left-auto z-[1000] max-w-sm bg-error-container border-2 border-error rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-2)] p-4 flex flex-col gap-3">
          <div className="flex items-center gap-2 text-on-error-container font-bold">
            <Icon name="warning" filled className="size-5" />
            Geofence Breach
          </div>
          <p className="text-sm text-on-error-container">
            <span className="font-semibold">{selectedAgent.fullName}</span> has been outside their assigned zone since{" "}
            {new Date(activeAlert.firstDetectedOutsideAt).toLocaleTimeString()}.
          </p>
          <div className="flex items-center justify-between text-sm">
            <span className="text-on-error-container/80">Liability at risk</span>
            <span className="font-bold tabular-nums text-on-error-container">
              {selectedAgent.balanceXaf !== null ? `${selectedAgent.balanceXaf.toLocaleString()} XAF` : "—"}
            </span>
          </div>
          <div className="flex items-center justify-between text-sm">
            <span className="text-on-error-container/80">Breach duration</span>
            <span className="font-bold tabular-nums text-on-error-container">{formatDuration(activeAlert.firstDetectedOutsideAt, nowMs)}</span>
          </div>
          <a
            href={`tel:${selectedAgent.phone}`}
            className="h-10 px-4 rounded-[var(--radius-sm)] bg-danger-red text-white text-sm font-semibold cursor-pointer flex items-center justify-center gap-2 transition-transform duration-150 ease-out hover:scale-[1.02] active:scale-95"
          >
            Call {selectedAgent.phone}
          </a>
        </div>
      )}
    </div>
  );
}
