import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { ScheduleDefaultsResponse } from "@/lib/types";

export async function GET() {
  try {
    const defaults = await api.get<ScheduleDefaultsResponse>("/admin/branches/schedule-defaults");
    return NextResponse.json(defaults);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}

export async function PUT(request: Request) {
  const body = await request.json();
  const overrideAll = new URL(request.url).searchParams.get("overrideAll") === "true";
  try {
    const defaults = await api.put<ScheduleDefaultsResponse>(`/admin/branches/schedule-defaults?overrideAll=${overrideAll}`, body);
    return NextResponse.json(defaults);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
