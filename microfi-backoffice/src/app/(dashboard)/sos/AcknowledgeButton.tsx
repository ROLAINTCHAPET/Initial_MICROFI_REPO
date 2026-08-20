"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";

export function AcknowledgeButton({ eventId }: { eventId: string }) {
  const router = useRouter();
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
        setError(body?.message ?? "Failed to acknowledge");
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        router.refresh();
      }, 500);
    } catch {
      setError("Unable to reach the server");
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
            Acknowledged
          </>
        ) : (
          "Acknowledge"
        )}
      </Button>
      {error && <p role="alert" className="text-xs text-danger-red">{error}</p>}
    </div>
  );
}
