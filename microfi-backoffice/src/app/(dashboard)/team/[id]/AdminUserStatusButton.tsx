"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import type { AdminUserStatus } from "@/lib/types";

export function AdminUserStatusButton({ userId, status }: { userId: string; status: AdminUserStatus }) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nextStatus: AdminUserStatus = status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";

  async function handleClick() {
    if (nextStatus === "SUSPENDED" && !confirm("Suspend this account? This immediately blocks sign-in.")) {
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/admin-users/${userId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: nextStatus }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? "Failed to update status");
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
    <div className="flex flex-col gap-2 items-start">
      <Button variant={succeeded ? "success" : nextStatus === "SUSPENDED" ? "danger" : "success"} loading={loading} disabled={succeeded} onClick={handleClick}>
        {succeeded ? (
          <>
            <Icon name="check-circle" className="size-5" />
            Done
          </>
        ) : nextStatus === "SUSPENDED" ? (
          "Suspend Account"
        ) : (
          "Reactivate Account"
        )}
      </Button>
      {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
    </div>
  );
}
