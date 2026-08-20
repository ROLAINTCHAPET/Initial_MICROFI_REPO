import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { BranchResponse } from "@/lib/types";

export async function PUT(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const body = await request.json();
  try {
    const branch = await api.put<BranchResponse>(`/admin/branches/${id}/schedule`, body);
    return NextResponse.json(branch);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
