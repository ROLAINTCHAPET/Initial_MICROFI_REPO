import { cookies } from "next/headers";
import type { AdminRole } from "./types";

export const SESSION_COOKIE = "microfi_session";

/**
 * Matches JwtService's claims for an ADMIN_USER principal (microfi-core
 * AdminAuthenticationController): {role, principalType, iss, sub, iat, exp}.
 */
export interface SessionClaims {
  role: AdminRole;
  branchId: string | null;
  sub: string; // admin login
  principalType: string;
  iat: number;
  exp: number;
}

/**
 * Decodes the JWT payload without verifying the signature — this is only ever used for local
 * UI routing decisions (which nav items to show, which branch to scope to). Kong already
 * verifies the signature on every real API call; re-verifying here would need the shared HMAC
 * secret in the frontend, which it must never have.
 */
export function decodeToken(token: string): SessionClaims | null {
  try {
    const payload = token.split(".")[1];
    const json = Buffer.from(payload, "base64url").toString("utf-8");
    const claims = JSON.parse(json) as SessionClaims;
    if (!claims.exp || claims.exp * 1000 < Date.now()) {
      return null;
    }
    return claims;
  } catch {
    return null;
  }
}

/** Server Components / Route Handlers only — reads the httpOnly session cookie. */
export async function getSession(): Promise<SessionClaims | null> {
  const store = await cookies();
  const token = store.get(SESSION_COOKIE)?.value;
  if (!token) return null;
  return decodeToken(token);
}

export async function getToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(SESSION_COOKIE)?.value ?? null;
}
