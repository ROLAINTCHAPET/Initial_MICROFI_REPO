"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

// Every page here is a Server Component — data is fetched once per navigation/reload, nothing
// re-fetches on its own (see OFJ/Cashier: no websocket or client-poll library anywhere in this
// app). Fine for most screens, but wrong for a "live oversight" page like /ofj or /cashier, where
// an admin sitting on it should see an agent's next collection show up without manually reloading
// — same reasoning as the mobile app's SOS/branch-notice polling, just via router.refresh() since
// there's no dedicated data endpoint to poll here, only the page's own server-rendered data.
// Renders nothing — purely a background timer. router.refresh() re-fetches server data in place;
// it doesn't remount client components, so an open modal's form state survives a tick.
export function AutoRefresh({ intervalMs = 10000 }: { intervalMs?: number }) {
  const router = useRouter();

  useEffect(() => {
    const id = setInterval(() => router.refresh(), intervalMs);
    return () => clearInterval(id);
  }, [router, intervalMs]);

  return null;
}
