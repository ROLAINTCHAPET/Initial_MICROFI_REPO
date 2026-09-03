import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";

interface LocationNameResponse {
  locationName: string | null;
}

export async function GET(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const { searchParams } = new URL(request.url);
  const lat = searchParams.get("lat");
  const lon = searchParams.get("lon");
  if (!lat || !lon) {
    return NextResponse.json({ message: "lat and lon query params are required" }, { status: 400 });
  }
  try {
    const result = await api.get<LocationNameResponse>(`/admin/agents/${id}/route/location-name?lat=${lat}&lon=${lon}`);
    return NextResponse.json(result);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
