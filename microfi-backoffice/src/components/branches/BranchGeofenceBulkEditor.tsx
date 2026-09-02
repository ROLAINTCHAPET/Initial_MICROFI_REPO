"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { GeofenceVertex } from "@/lib/types";

const TrackingMap = dynamic(() => import("@/app/(dashboard)/tracking/TrackingMap").then((m) => m.TrackingMap), {
  ssr: false,
  loading: () => <div className="h-full w-full flex items-center justify-center text-sm text-on-surface-variant">Loading map…</div>,
});

// A full page rather than a popup, same reasoning as AgentGeofenceEditor — a map needs room, and
// this bulk convenience (see GeofenceService#applyGeofenceToBranch) is its own destination.
export function BranchGeofenceBulkEditor({ branchId, branchLabel }: { branchId: string; branchLabel: string }) {
  const dict = useDictionary();
  const [draftVertices, setDraftVertices] = useState<GeofenceVertex[]>([]);
  const [confirmed, setConfirmed] = useState(false);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);

  async function apply() {
    if (draftVertices.length < 3) {
      setError(dict.tracking.workspace.geofenceMinPoints);
      return;
    }
    setApplying(true);
    setError(null);
    try {
      const res = await fetch(`/api/branches/${branchId}/geofence`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ vertices: draftVertices }),
      });
      const body = await res.json().catch(() => null);
      if (!res.ok) {
        setError(body?.message ?? dict.branches.geofenceBulk.failedToApply);
        return;
      }
      setResult(body?.message ?? null);
      setDraftVertices([]);
      setConfirmed(false);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setApplying(false);
    }
  }

  return (
    <div className="relative rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden bg-surface-container-low h-[calc(100vh-260px)] min-h-[480px]">
      <TrackingMap
        points={[]}
        transactions={[]}
        geofence={null}
        editingVertices={draftVertices}
        onMapClick={(lat, lon) => setDraftVertices((v) => [...v, { lat: Math.round(lat * 1e5) / 1e5, lon: Math.round(lon * 1e5) / 1e5 }])}
      />

      <div className="absolute top-3 right-3 z-[1000] flex flex-col items-end gap-3 w-full sm:w-auto px-3 sm:px-0">
        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] px-4 py-3 w-full sm:w-96 max-w-full">
          <p className="font-bold text-sm text-primary leading-tight">{branchLabel}</p>
          <p className="text-xs text-on-surface-variant mt-1">{dict.branches.geofenceBulk.sectionHint}</p>
        </div>

        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] shadow-[var(--shadow-elevation-1)] px-4 py-3 flex flex-col gap-3 w-full sm:w-96 max-w-full">
          <p className="text-xs text-on-surface-variant">{t(dict.tracking.workspace.placeVertices, { count: draftVertices.length })}</p>
          {result ? (
            <p className="text-sm text-secondary font-semibold">{dict.branches.geofenceBulk.appliedPrefix}{result}</p>
          ) : (
            <>
              <label className="flex items-start gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={confirmed}
                  onChange={(e) => setConfirmed(e.target.checked)}
                  className="mt-0.5 size-4 cursor-pointer accent-primary shrink-0"
                />
                <span className="text-xs text-on-surface">{dict.branches.geofenceBulk.confirmLabel}</span>
              </label>
              {error && <p role="alert" className="text-xs text-danger-red">{error}</p>}
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setDraftVertices((v) => v.slice(0, -1))}
                  disabled={draftVertices.length === 0}
                  className="h-9 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant text-primary text-xs font-semibold cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed hover:bg-surface-container-low transition-colors"
                >
                  {dict.tracking.workspace.undoPoint}
                </button>
                <button
                  onClick={apply}
                  disabled={!confirmed || draftVertices.length < 3 || applying}
                  className="h-9 px-3 flex-1 rounded-[var(--radius-sm)] bg-primary text-on-primary text-xs font-semibold cursor-pointer disabled:opacity-60 transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95"
                >
                  {applying ? dict.branches.geofenceBulk.applying : dict.branches.geofenceBulk.applyButton}
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
