import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { AdminUserResponse } from "@/lib/types";

export async function POST(request: Request) {
  const body = await request.json();
  try {
    const user = await api.post<AdminUserResponse>("/admin/users", body);
    return NextResponse.json(user, { status: 201 });
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
