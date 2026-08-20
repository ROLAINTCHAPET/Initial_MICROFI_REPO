import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { GeofenceResponse } from "@/lib/types";

export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  try {
    const geofence = await api.get<GeofenceResponse>(`/admin/agents/${id}/geofence`);
    return NextResponse.json(geofence);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}

export async function PUT(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const body = await request.json();
  try {
    const geofence = await api.put<GeofenceResponse>(`/admin/agents/${id}/geofence`, body);
    return NextResponse.json(geofence);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
