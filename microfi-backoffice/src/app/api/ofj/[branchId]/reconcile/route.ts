import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { OfjAgentLineResponse } from "@/lib/types";

export async function POST(request: Request, context: { params: Promise<{ branchId: string }> }) {
  const { branchId } = await context.params;
  const body = await request.json();
  try {
    const line = await api.post<OfjAgentLineResponse>(`/ofj/${branchId}/reconcile`, body);
    return NextResponse.json(line);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
