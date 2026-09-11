import { API_BASE_URL } from "@/lib/api";
import { getToken } from "@/lib/auth";

// Same reasoning as /api/collection-rejections/stream: the browser's native EventSource can't set
// an Authorization header, so this route authenticates server-side and pipes Core's SSE response
// body straight through rather than buffering it.
export const dynamic = "force-dynamic";

export async function GET() {
  const token = await getToken();
  if (!token) {
    return new Response(null, { status: 401 });
  }

  const upstream = await fetch(`${API_BASE_URL}/admin/misconduct-reports/stream`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
    cache: "no-store",
  });

  if (!upstream.ok || !upstream.body) {
    return new Response(null, { status: upstream.status || 502 });
  }

  return new Response(upstream.body, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
    },
  });
}
