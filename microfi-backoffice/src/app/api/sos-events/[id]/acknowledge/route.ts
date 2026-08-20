import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { SosResponse } from "@/lib/types";

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  try {
    const event = await api.patch<SosResponse>(`/admin/sos-events/${id}/acknowledge`);
    return NextResponse.json(event);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
