import { NextResponse } from "next/server";
import { ApiRequestError, apiFetch } from "@/lib/api";
import type { VarianceDebtResponse } from "@/lib/types";

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const formData = await request.formData();
  try {
    const debt = await apiFetch<VarianceDebtResponse>(`/admin/variance-debts/${id}/write-off`, {
      method: "PATCH",
      body: formData,
    });
    return NextResponse.json(debt);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
