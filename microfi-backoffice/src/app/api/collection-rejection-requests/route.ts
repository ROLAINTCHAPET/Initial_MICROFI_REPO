import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { CollectionRejectionRequestResponse } from "@/lib/types";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const status = searchParams.get("status");
  const query = status ? `?status=${status}` : "";
  try {
    const requests = await api.get<CollectionRejectionRequestResponse[]>(`/admin/collection-rejection-requests${query}`);
    return NextResponse.json(requests);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
