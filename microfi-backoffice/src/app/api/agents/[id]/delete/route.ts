import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { AgentResponse } from "@/lib/types";

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const body = await request.json();
  try {
    const agent = await api.patch<AgentResponse>(`/admin/agents/${id}/delete`, body);
    return NextResponse.json(agent);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
