import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { AuditLogResponse } from "@/lib/types";

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  try {
    const query = new URLSearchParams();
    for (const key of ["from", "to", "branchId", "category", "actorType"]) {
      const value = searchParams.get(key);
      if (value) query.set(key, value);
    }
    const logs = await api.get<AuditLogResponse[]>(`/admin/audit-log?${query.toString()}`);
    return NextResponse.json(logs);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
