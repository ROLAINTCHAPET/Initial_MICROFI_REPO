import { NextResponse } from "next/server";
import { ApiRequestError, api, apiFetch } from "@/lib/api";
import type { RegistrationApplicationResponse } from "@/lib/types";

export async function POST(request: Request) {
  const formData = await request.formData();
  try {
    const application = await apiFetch<RegistrationApplicationResponse>("/admin/registration-applications", {
      method: "POST",
      body: formData,
    });
    return NextResponse.json(application, { status: 201 });
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}

export async function GET(request: Request) {
  const { searchParams } = new URL(request.url);
  const status = searchParams.get("status");
  try {
    const applications = await api.get<RegistrationApplicationResponse[]>(
      `/admin/registration-applications${status ? `?status=${status}` : ""}`
    );
    return NextResponse.json(applications);
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
