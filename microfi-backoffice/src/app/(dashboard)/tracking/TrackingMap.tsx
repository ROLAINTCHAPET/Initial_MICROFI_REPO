"use client";

import "leaflet/dist/leaflet.css";
import { useEffect, useMemo } from "react";
import { MapContainer, TileLayer, Polyline, Polygon, CircleMarker, Popup, useMap, useMapEvents } from "react-leaflet";
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
  points,
  transactions,
  geofence,
  editingVertices,
  onMapClick,
}: {
  points: RoutePointResponse[];
  transactions: RouteTransactionResponse[];
  geofence: GeofenceVertex[] | null;
  editingVertices: GeofenceVertex[] | null;
  onMapClick?: (lat: number, lon: number) => void;
}) {
  const routeLatLngs = useMemo<[number, number][]>(() => points.map((p) => [p.lat, p.lon]), [points]);
  const geofenceLatLngs = useMemo<[number, number][]>(() => (geofence ?? []).map((v) => [v.lat, v.lon]), [geofence]);
  const draftLatLngs = useMemo<[number, number][]>(() => (editingVertices ?? []).map((v) => [v.lat, v.lon]), [editingVertices]);

  const boundsSource = routeLatLngs.length > 0 ? routeLatLngs : geofenceLatLngs.length > 0 ? geofenceLatLngs : [];

  return (
    <MapContainer center={DOUALA_FALLBACK} zoom={13} className="h-full w-full" scrollWheelZoom zoomControl={false}>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <FitBounds points={boundsSource} />
      {onMapClick && <ClickCapture onClick={onMapClick} />}

      {routeLatLngs.length > 1 && <Polyline positions={routeLatLngs} pathOptions={{ color: "#000f22", weight: 3 }} />}
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
        >
          <Popup>{new Date(points[i].recordedAt).toLocaleTimeString()}</Popup>
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
