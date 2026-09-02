import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { ClientResponse } from "@/lib/types";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const branchId = searchParams.get("branchId");
  if (!branchId) {
    return NextResponse.json({ message: "branchId is required" }, { status: 400 });
  }
  try {
    const clients = await api.get<ClientResponse[]>(`/admin/clients?branchId=${branchId}`);
    return NextResponse.json(clients);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
