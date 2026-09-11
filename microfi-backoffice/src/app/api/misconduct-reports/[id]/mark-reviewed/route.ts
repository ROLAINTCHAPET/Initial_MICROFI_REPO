import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { AgentMisconductReportResponse } from "@/lib/types";

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  try {
    const result = await api.patch<AgentMisconductReportResponse>(`/admin/misconduct-reports/${id}/mark-reviewed`);
    return NextResponse.json(result);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
