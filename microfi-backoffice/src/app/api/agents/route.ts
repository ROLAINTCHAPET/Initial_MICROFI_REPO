import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { AgentResponse } from "@/lib/types";

export async function POST(request: Request) {
  const body = await request.json();
  try {
    const agent = await api.post<AgentResponse>("/admin/agents", body);
    return NextResponse.json(agent, { status: 201 });
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
