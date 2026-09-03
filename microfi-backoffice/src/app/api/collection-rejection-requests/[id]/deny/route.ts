import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { CollectionRejectionRequestResponse } from "@/lib/types";

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const body = await request.json();
  try {
    const result = await api.patch<CollectionRejectionRequestResponse>(`/admin/collection-rejection-requests/${id}/deny`, body);
    return NextResponse.json(result);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
