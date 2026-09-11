import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { AgentMisconductReportResponse } from "@/lib/types";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const unresolvedOnly = searchParams.get("unresolvedOnly");
  const query = unresolvedOnly ? `?unresolvedOnly=${unresolvedOnly}` : "";
  try {
    const reports = await api.get<AgentMisconductReportResponse[]>(`/admin/misconduct-reports${query}`);
    return NextResponse.json(reports);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
