import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";

interface AvailabilityResponse {
  available: boolean;
}

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const field = searchParams.get("field");
  const value = searchParams.get("value") ?? "";
  if (!field) {
    return NextResponse.json({ message: "field is required" }, { status: 400 });
  }
  try {
    const result = await api.get<AvailabilityResponse>(
      `/admin/registration-applications/availability?field=${encodeURIComponent(field)}&value=${encodeURIComponent(value)}`
    );
    return NextResponse.json(result);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
