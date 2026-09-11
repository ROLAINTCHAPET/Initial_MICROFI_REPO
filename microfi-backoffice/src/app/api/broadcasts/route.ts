import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { BroadcastMessageResponse } from "@/lib/types";

export async function GET() {
  try {
    const messages = await api.get<BroadcastMessageResponse[]>("/admin/broadcasts");
    return NextResponse.json(messages);
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
    const message = await api.post<BroadcastMessageResponse>("/admin/broadcasts", body);
    return NextResponse.json(message, { status: 201 });
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
