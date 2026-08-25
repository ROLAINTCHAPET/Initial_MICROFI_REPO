"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function AcknowledgeButton({ eventId }: { eventId: string }) {
  const router = useRouter();
  const dict = useDictionary();
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick() {
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/sos-events/${eventId}/acknowledge`, { method: "PATCH" });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.sos.failedToAcknowledge);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        router.refresh();
      }, 500);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col gap-1 items-start">
      <Button variant={succeeded ? "success" : "danger"} loading={loading} disabled={succeeded} onClick={handleClick}>
        {succeeded ? (
          <>
            <Icon name="check-circle" className="size-5" />
            {dict.common.status.ACKNOWLEDGED}
          </>
        ) : (
          dict.sos.acknowledgeAction
        )}
      </Button>
      {error && <p role="alert" className="text-xs text-danger-red">{error}</p>}
    </div>
  );
}
