"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ActionCard } from "@/components/ActionCard";
import type { AdminUserStatus } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function AdminUserStatusButton({ userId, status }: { userId: string; status: AdminUserStatus }) {
  const router = useRouter();
  const dict = useDictionary();
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const nextStatus: AdminUserStatus = status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";

  async function handleClick() {
    if (nextStatus === "SUSPENDED" && !confirm(dict.team.statusButton.confirmSuspend)) {
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
        setError(body?.message ?? dict.team.statusButton.failedToUpdateStatus);
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

  const isSuspendAction = nextStatus === "SUSPENDED";

  return (
    <div className="flex flex-col gap-2">
      <ActionCard
        icon={succeeded ? "check-circle" : isSuspendAction ? "warning" : "check-circle"}
        title={succeeded ? dict.team.statusButton.done : isSuspendAction ? dict.team.statusButton.suspendAccount : dict.team.statusButton.reactivateAccount}
        description={isSuspendAction ? dict.team.statusButton.suspendDescription : dict.team.statusButton.reactivateDescription}
        danger={isSuspendAction}
        disabled={succeeded || loading}
        onClick={handleClick}
      />
      {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
    </div>
  );
}
