import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { CollectionResponse } from "@/lib/types";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const branchId = searchParams.get("branchId");
  const from = searchParams.get("from");
  const to = searchParams.get("to");
  try {
    const collections = await api.get<CollectionResponse[]>(
      `/admin/clients/collections?branchId=${branchId}&from=${from}&to=${to}`
    );
    return NextResponse.json(collections);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
