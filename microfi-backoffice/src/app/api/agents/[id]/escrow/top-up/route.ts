import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { EscrowResponse } from "@/lib/types";

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const body = await request.json();
  try {
    const escrow = await api.post<EscrowResponse>(`/agents/${id}/escrow/top-up`, body);
    return NextResponse.json(escrow);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
