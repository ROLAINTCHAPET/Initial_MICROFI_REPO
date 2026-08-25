import { NextResponse } from "next/server";
import { ApiRequestError, apiFetch } from "@/lib/api";
import type { EscrowResponse } from "@/lib/types";

export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const formData = await request.formData();
  try {
    const escrow = await apiFetch<EscrowResponse>(`/agents/${id}/escrow/top-up`, {
      method: "POST",
      body: formData,
    });
    return NextResponse.json(escrow);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
