import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { ApiRequestError, apiFetch } from "@/lib/api";
import { SESSION_COOKIE, decodeToken } from "@/lib/auth";
import type { AuthResponse } from "@/lib/types";

// Thin BFF: the browser never sees the JWT. It posts credentials here; this Route Handler calls
// Kong server-side and stores the token as an httpOnly cookie in the response.
export async function POST(request: Request) {
  const { login, password } = await request.json();

  if (!login || !password) {
    return NextResponse.json({ message: "Login and password are required" }, { status: 400 });
  }

  try {
    const { token } = await apiFetch<AuthResponse>("/auth/admin/login", {
      method: "POST",
      body: JSON.stringify({ login, password }),
    });

    const claims = decodeToken(token);
    if (!claims) {
      return NextResponse.json({ message: "Received an invalid session token" }, { status: 502 });
    }

    const store = await cookies();
    store.set(SESSION_COOKIE, token, {
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      path: "/",
      expires: new Date(claims.exp * 1000),
    });

    return NextResponse.json({ role: claims.role, branchId: claims.branchId });
  } catch (err) {
    if (err instanceof ApiRequestError) {
      return NextResponse.json({ message: err.message }, { status: err.status });
    }
    return NextResponse.json({ message: "Unable to reach the backend" }, { status: 502 });
  }
}
