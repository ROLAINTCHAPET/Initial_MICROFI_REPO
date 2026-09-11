import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { BranchResponse } from "@/lib/types";

export async function GET() {
  try {
    const branches = await api.get<BranchResponse[]>("/admin/branches");
    return NextResponse.json(branches);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}

export async function POST(request: Request) {
  const body = await request.json();
  try {
    const branch = await api.post<BranchResponse>("/admin/branches", body);
    return NextResponse.json(branch, { status: 201 });
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
