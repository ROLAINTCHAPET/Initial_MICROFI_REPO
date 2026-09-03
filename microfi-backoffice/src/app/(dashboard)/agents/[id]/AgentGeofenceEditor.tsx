"use client";

import { useState, useEffect } from "react";
import dynamic from "next/dynamic";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { GeofenceResponse, GeofenceVertex } from "@/lib/types";

const TrackingMap = dynamic(() => import("../../tracking/TrackingMap").then((m) => m.TrackingMap), {
  ssr: false,
  loading: () => <div className="h-full w-full flex items-center justify-center text-sm text-on-surface-variant">Loading map…</div>,
});

// A full page rather than a popup — a map needs room, and this is a destination in its own
// right (reached from the agent's Administration tab), not a quick one-field confirmation.
export function AgentGeofenceEditor({ agentId, agentLabel }: { agentId: string; agentLabel: string }) {
  const dict = useDictionary();
  const [loaded, setLoaded] = useState(false);
  const [geofence, setGeofence] = useState<GeofenceResponse | null>(null);
  const [editing, setEditing] = useState(false);
  const [draftVertices, setDraftVertices] = useState<GeofenceVertex[]>([]);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch(`/api/agents/${agentId}/geofence`)
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        setGeofence(data);
        setLoaded(true);
      });
  }, [agentId]);

  function startEditing() {
    setEditing(true);
    setDraftVertices([]);
    setError(null);
  }

  function cancelEditing() {
    setEditing(false);
    setDraftVertices([]);
    setError(null);
  }

  async function save() {
    if (draftVertices.length < 3) {
      setError(dict.tracking.workspace.geofenceMinPoints);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const res = await fetch(`/api/agents/${agentId}/geofence`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ vertices: draftVertices }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.tracking.workspace.geofenceSaveFailed);
        return;
      }
      const saved = await res.json();
      setGeofence(saved);
      setEditing(false);
      setDraftVertices([]);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (!confirm(dict.tracking.workspace.confirmDeleteGeofence)) {
      return;
    }
    setDeleting(true);
    setError(null);
    try {
      const res = await fetch(`/api/agents/${agentId}/geofence`, { method: "DELETE" });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.tracking.workspace.geofenceDeleteFailed);
        return;
      }
      setGeofence(null);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="relative rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden bg-surface-container-low h-[calc(100vh-260px)] min-h-[480px]">
      {!loaded ? (
        <div className="h-full w-full flex items-center justify-center text-sm text-on-surface-variant">{dict.common.loading}</div>
      ) : (
        <TrackingMap
          points={[]}
          transactions={[]}
          geofence={geofence?.vertices ?? null}
          editingVertices={editing ? draftVertices : null}
          onMapClick={editing ? (lat, lon) => setDraftVertices((v) => [...v, { lat: Math.round(lat * 1e5) / 1e5, lon: Math.round(lon * 1e5) / 1e5 }]) : undefined}
        />
      )}

      <div className="absolute top-3 right-3 z-[1000] flex flex-col items-end gap-3 w-full sm:w-auto px-3 sm:px-0">
        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] px-4 py-3 w-full sm:w-auto">
          <p className="font-bold text-sm text-primary leading-tight">{agentLabel}</p>
        </div>

        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] px-4 py-3 flex flex-col gap-2 w-full sm:w-auto sm:min-w-[280px]">
          {!editing ? (
            <div className="flex items-center gap-2">
              <button
                onClick={startEditing}
                className="h-9 px-4 rounded-[var(--radius-sm)] bg-primary text-on-primary text-sm font-semibold cursor-pointer flex items-center justify-center gap-2 transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95"
              >
                <Icon name="pencil" className="size-4" />
                {geofence ? dict.tracking.workspace.editGeofence : dict.tracking.workspace.setGeofence}
              </button>
              {geofence && (
                <button
                  onClick={remove}
                  disabled={deleting}
                  className="h-9 px-3 rounded-[var(--radius-sm)] border-2 border-danger-red text-danger-red text-sm font-semibold cursor-pointer disabled:opacity-60 flex items-center justify-center gap-2 hover:bg-error-container transition-colors"
                >
                  <Icon name="trash" className="size-4" />
                  {deleting ? dict.tracking.workspace.deletingGeofence : dict.tracking.workspace.deleteGeofence}
                </button>
              )}
            </div>
          ) : (
            <>
              <p className="text-xs text-on-surface-variant">{t(dict.tracking.workspace.placeVertices, { count: draftVertices.length })}</p>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setDraftVertices((v) => v.slice(0, -1))}
                  disabled={draftVertices.length === 0}
                  className="h-9 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant text-primary text-xs font-semibold cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed hover:bg-surface-container-low transition-colors"
                >
                  {dict.tracking.workspace.undoPoint}
                </button>
                <button
                  onClick={cancelEditing}
                  className="h-9 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant text-primary text-xs font-semibold cursor-pointer hover:bg-surface-container-low transition-colors"
                >
                  {dict.common.cancel}
                </button>
                <button
                  onClick={save}
                  disabled={saving}
                  className="h-9 px-3 rounded-[var(--radius-sm)] bg-primary text-on-primary text-xs font-semibold cursor-pointer disabled:opacity-60 transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95"
                >
                  {saving ? dict.tracking.workspace.savingGeofence : dict.tracking.workspace.saveGeofence}
                </button>
              </div>
            </>
          )}
          {error && <p role="alert" className="text-xs text-danger-red">{error}</p>}
        </div>
      </div>
    </div>
  );
}
