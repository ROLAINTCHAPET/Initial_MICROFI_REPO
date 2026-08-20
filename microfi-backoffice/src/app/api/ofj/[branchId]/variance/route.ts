import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { VarianceDebtResponse } from "@/lib/types";

export async function POST(request: Request, context: { params: Promise<{ branchId: string }> }) {
  const { branchId } = await context.params;
  const body = await request.json();
  try {
    const debt = await api.post<VarianceDebtResponse>(`/ofj/${branchId}/variance`, body);
    return NextResponse.json(debt);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
