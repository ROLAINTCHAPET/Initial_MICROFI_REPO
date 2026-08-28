import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { CollectionResponse } from "@/lib/types";

export async function GET(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  const date = new URL(request.url).searchParams.get("date");
  if (!date) {
    return NextResponse.json({ message: "date query param is required" }, { status: 400 });
  }
  try {
    const collections = await api.get<CollectionResponse[]>(`/admin/agents/${id}/collections?date=${date}`);
    return NextResponse.json(collections);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
