import { getToken } from "./auth";
import type { ApiError } from "./types";

// Server-side only: Route Handlers/Server Components run in Node and can reach Kong directly.
// Never exposed to the browser — the token never leaves the server (see lib/auth.ts). Exported
// so a route handler needing a raw (non-JSON) passthrough — e.g. a binary document download —
// can reuse the same base URL instead of re-deriving it.
export const API_BASE_URL = process.env.MICROFI_API_BASE_URL ?? "http://localhost:8000/api/v1";

export class ApiRequestError extends Error {
  status: number;
  body: ApiError | null;

  constructor(status: number, body: ApiError | null, message: string) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.body = body;
  }
}

/**
 * Attaches the caller's session JWT (read server-side from the httpOnly cookie) and talks to
 * Kong — the same gateway path a mobile client would use, so this exercises the real JWT
 * validation, rate-limiting, and routing rather than bypassing them.
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await getToken();
  const headers = new Headers(init?.headers);
  // A FormData body (multipart uploads) must NOT get an explicit Content-Type — fetch derives
  // the multipart boundary itself only when the header is left unset. No existing caller passes
  // FormData today, so this is a no-op for every other call site.
  if (!(init?.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const errorBody = data as ApiError | null;
    throw new ApiRequestError(
      response.status,
      errorBody,
      errorBody?.message ?? errorBody?.error ?? `Request failed with status ${response.status}`
    );
  }

  return data as T;
}

export const api = {
  get: <T>(path: string) => apiFetch<T>(path, { method: "GET" }),
  post: <T>(path: string, body?: unknown) =>
    apiFetch<T>(path, { method: "POST", body: body !== undefined ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    apiFetch<T>(path, { method: "PATCH", body: body !== undefined ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    apiFetch<T>(path, { method: "PUT", body: body !== undefined ? JSON.stringify(body) : undefined }),
};
