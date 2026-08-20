import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { GeofenceAlertResponse } from "@/lib/types";

export async function GET(_request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  try {
    const alerts = await api.get<GeofenceAlertResponse[]>(`/admin/agents/${id}/geofence-alerts`);
    return NextResponse.json(alerts);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
