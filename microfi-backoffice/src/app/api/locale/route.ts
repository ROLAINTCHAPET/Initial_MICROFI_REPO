import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { isLocale } from "@/lib/i18n/dictionaries";
import { LOCALE_COOKIE } from "@/lib/i18n/locale";

export async function POST(request: Request) {
  const { locale } = await request.json();

  if (typeof locale !== "string" || !isLocale(locale)) {
    return NextResponse.json({ message: "Unsupported locale" }, { status: 400 });
  }

  const store = await cookies();
  // Not httpOnly, unlike the session cookie — this is a display preference, not a credential,
  // and doesn't need to be hidden from client JS.
  store.set(LOCALE_COOKIE, locale, {
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: 60 * 60 * 24 * 365,
  });

  return NextResponse.json({ ok: true });
}
