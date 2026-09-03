import { NextResponse } from "next/server";
import { ApiRequestError, apiFetch } from "@/lib/api";
import type { CollectionRejectionRequestResponse } from "@/lib/types";

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const formData = await request.formData();
  try {
    const result = await apiFetch<CollectionRejectionRequestResponse>(`/admin/collection-rejection-requests/${id}/approve`, {
      method: "PATCH",
      body: formData,
    });
    return NextResponse.json(result);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
