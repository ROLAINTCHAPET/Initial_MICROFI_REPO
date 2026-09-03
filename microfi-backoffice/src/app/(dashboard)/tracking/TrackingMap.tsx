"use client";

import "leaflet/dist/leaflet.css";
import { useEffect, useMemo, useState } from "react";
import { MapContainer, TileLayer, Polyline, Polygon, CircleMarker, Popup, Tooltip, useMap, useMapEvents } from "react-leaflet";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import type { GeofenceVertex, RoutePointResponse, RouteTransactionResponse } from "@/lib/types";

const DOUALA_FALLBACK: [number, number] = [4.05, 9.7];

function FitBounds({ points }: { points: [number, number][] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 0) return;
    if (points.length === 1) {
      map.setView(points[0], 14);
      return;
    }
    map.fitBounds(points, { padding: [40, 40] });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(points)]);
  return null;
}

function ClickCapture({ onClick }: { onClick: (lat: number, lon: number) => void }) {
  useMapEvents({
    click(e) {
      onClick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

export function TrackingMap({
  agentId,
  points,
  transactions,
  geofence,
  editingVertices,
  onMapClick,
}: {
  agentId?: string;
  points: RoutePointResponse[];
  transactions: RouteTransactionResponse[];
  geofence: GeofenceVertex[] | null;
  editingVertices: GeofenceVertex[] | null;
  onMapClick?: (lat: number, lon: number) => void;
}) {
  const dict = useDictionary();
  const routeLatLngs = useMemo<[number, number][]>(() => points.map((p) => [p.lat, p.lon]), [points]);
  const geofenceLatLngs = useMemo<[number, number][]>(() => (geofence ?? []).map((v) => [v.lat, v.lon]), [geofence]);
  const draftLatLngs = useMemo<[number, number][]>(() => (editingVertices ?? []).map((v) => [v.lat, v.lon]), [editingVertices]);

  // Resolved lazily per click, not pre-fetched for every ~5-minute ping — most pings on a busy
  // day's route are never clicked, so eagerly reverse-geocoding all of them would multiply
  // Nominatim's free-tier call volume for no benefit (see AdminTrackingController#locationName).
  // Keyed by the ping's own recordedAt (stable across TrackingWorkspace's 10s poll, which hands
  // down a brand new `points` array each time) rather than array index — a resolved name must
  // survive the next poll instead of being wiped and re-fetched from scratch every 10 seconds.
  // Absent = not yet requested, null = resolved with no name available.
  const [pingLocationNames, setPingLocationNames] = useState<Record<string, string | null>>({});

  function resolvePingLocation(recordedAt: string, lat: number, lon: number) {
    if (!agentId || recordedAt in pingLocationNames) return;
    fetch(`/api/agents/${agentId}/route/location-name?lat=${lat}&lon=${lon}`)
      .then((r) => (r.ok ? r.json() : { locationName: null }))
      .then((data) => setPingLocationNames((m) => ({ ...m, [recordedAt]: data.locationName ?? null })))
      .catch(() => setPingLocationNames((m) => ({ ...m, [recordedAt]: null })));
  }

  const boundsSource = routeLatLngs.length > 0 ? routeLatLngs : geofenceLatLngs.length > 0 ? geofenceLatLngs : [];

  return (
    <MapContainer center={DOUALA_FALLBACK} zoom={13} className="h-full w-full" scrollWheelZoom zoomControl={false}>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitBounds points={boundsSource} />
      {onMapClick && <ClickCapture onClick={onMapClick} />}

      {/* The agent's track, connecting every ~5-minute GPS ping in order — see
          location_tracking_service.dart's fixed interval on the mobile side — so the path walked
          reads as a continuous line, not a scatter of unconnected dots. */}
      {routeLatLngs.length > 1 && <Polyline positions={routeLatLngs} pathOptions={{ color: "#000f22", weight: 4, opacity: 0.85 }} />}
      {routeLatLngs.map((pos, i) => (
        <CircleMarker
          key={`pt-${i}`}
          center={pos}
          radius={i === routeLatLngs.length - 1 ? 7 : 4}
          pathOptions={
            i === routeLatLngs.length - 1
              ? { color: "#000f22", fillColor: "#000f22", fillOpacity: 1, weight: 2 }
              : { color: "#000f22", fillColor: "#ffffff", fillOpacity: 1, weight: 1.5 }
          }
          eventHandlers={{ click: () => resolvePingLocation(points[i].recordedAt, pos[0], pos[1]) }}
        >
          {/* permanent (not click-to-reveal like Popup) — the whole point is that each ~5-minute
              ping's time is visible at a glance without hunting for it. */}
          <Tooltip permanent direction="top" offset={[0, -6]} className="ping-time-label" opacity={1}>
            {new Date(points[i].recordedAt).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}
          </Tooltip>
          {/* Click-to-reveal, unlike the always-visible Tooltip above — the location name is
              resolved on demand (see resolvePingLocation), so it isn't known until clicked. */}
          <Popup>
            {new Date(points[i].recordedAt).toLocaleTimeString()}
            <br />
            {!(points[i].recordedAt in pingLocationNames)
              ? dict.tracking.workspace.resolvingLocation
              : (pingLocationNames[points[i].recordedAt] ?? dict.tracking.workspace.locationNameUnavailable)}
          </Popup>
        </CircleMarker>
      ))}

      {transactions.map((t) => (
        <CircleMarker
          key={t.collectionId}
          center={[t.lat, t.lon]}
          radius={7}
          pathOptions={{ color: "#006b59", fillColor: "#55fcd8", fillOpacity: 0.9, weight: 2 }}
        >
          <Popup>
            {t.amountXaf.toLocaleString()} XAF
            <br />
            {new Date(t.collectedAt).toLocaleTimeString()}
            <br />
            {t.locationName ?? `${t.lat.toFixed(5)}, ${t.lon.toFixed(5)}`}
          </Popup>
        </CircleMarker>
      ))}

      {geofenceLatLngs.length >= 3 && (
        <Polygon positions={geofenceLatLngs} pathOptions={{ color: "#006b59", fillColor: "#55fcd8", fillOpacity: 0.12, weight: 2 }} />
      )}

      {draftLatLngs.length > 0 && (
        <Polygon positions={draftLatLngs} pathOptions={{ color: "#ba1a1a", fillColor: "#ba1a1a", fillOpacity: 0.1, weight: 2, dashArray: "6 4" }} />
      )}
      {draftLatLngs.map((pos, i) => (
        <CircleMarker key={`draft-${i}`} center={pos} radius={5} pathOptions={{ color: "#ba1a1a", fillColor: "#ba1a1a", fillOpacity: 1, weight: 1.5 }} />
      ))}
    </MapContainer>
  );
}
