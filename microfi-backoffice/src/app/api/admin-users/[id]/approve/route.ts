import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { AdminUserResponse } from "@/lib/types";

export async function PATCH(_request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  try {
    const user = await api.patch<AdminUserResponse>(`/admin/users/${id}/approve`, {});
    return NextResponse.json(user);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
