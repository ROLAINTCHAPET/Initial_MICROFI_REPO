import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { SESSION_COOKIE, decodeToken } from "@/lib/auth";

// Renamed from middleware.ts in Next.js 16 (see AGENTS.md). Route-level guard only — every
// Route Handler and Server Component also checks the session itself (see lib/api.ts / lib/auth.ts),
// per Next's own guidance not to rely on Proxy alone for auth.
const PUBLIC_PATHS = ["/login"];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (PUBLIC_PATHS.includes(pathname) || pathname.startsWith("/api/auth/") || pathname.startsWith("/backgrounds/")) {
    return NextResponse.next();
  }

  const token = request.cookies.get(SESSION_COOKIE)?.value;
  const claims = token ? decodeToken(token) : null;

  if (!claims) {
    const loginUrl = new URL("/login", request.url);
    return NextResponse.redirect(loginUrl);
  }

  // We now serve the regional dashboard at / instead of redirecting.
  return NextResponse.next();
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|api/auth).*)"],
};
