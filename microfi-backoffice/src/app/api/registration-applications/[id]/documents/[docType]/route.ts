import { NextResponse } from "next/server";
import { API_BASE_URL } from "@/lib/api";
import { getToken } from "@/lib/auth";

// Binary passthrough — can't reuse api.get<T>() (always JSON.parses the body). Streams the
// document's raw bytes straight through with its real Content-Type, same auth as every other
// admin call, so the storage path itself is never exposed to the browser.
export async function GET(request: Request, context: { params: Promise<{ id: string; docType: string }> }) {
  const { id, docType } = await context.params;
  const token = await getToken();
  const headers = new Headers();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}/admin/registration-applications/${id}/documents/${docType}`, {
    headers,
    cache: "no-store",
  });

  if (!response.ok) {
    const text = await response.text().catch(() => "");
    return NextResponse.json({ message: text || "Unable to fetch document" }, { status: response.status });
  }

  const contentType = response.headers.get("Content-Type") ?? "application/octet-stream";
  const buffer = await response.arrayBuffer();
  return new NextResponse(buffer, { headers: { "Content-Type": contentType } });
}
