import { NextResponse } from "next/server";
import { ApiRequestError, api } from "@/lib/api";
import type { RegistrationApplicationResponse } from "@/lib/types";

export async function GET(request: Request, context: { params: Promise<{ id: string }> }) {
  const { id } = await context.params;
  try {
    const application = await api.get<RegistrationApplicationResponse>(`/admin/registration-applications/${id}`);
    return NextResponse.json(application);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
