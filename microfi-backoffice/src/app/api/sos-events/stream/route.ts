import { API_BASE_URL } from "@/lib/api";
import { getToken } from "@/lib/auth";

// The browser's native EventSource can't set an Authorization header, and this app deliberately
// never exposes the raw JWT to client-side JS (the session cookie is httpOnly) — so this route
// authenticates server-side the same way every other proxy route here does, then pipes Core's SSE
// response body straight through rather than buffering it (buffering would defeat the whole point
// of a stream: nothing would reach the browser until the connection eventually closed).
export const dynamic = "force-dynamic";

export async function GET() {
  const token = await getToken();
  if (!token) {
    return new Response(null, { status: 401 });
  }

  const upstream = await fetch(`${API_BASE_URL}/admin/sos-events/stream`, {
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
