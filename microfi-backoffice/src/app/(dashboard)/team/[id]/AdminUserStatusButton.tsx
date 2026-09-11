"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ActionCard } from "@/components/ActionCard";
import type { AdminUserStatus } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function AdminUserStatusButton({ userId, status, isAdmin }: { userId: string; status: AdminUserStatus; isAdmin: boolean }) {
  const router = useRouter();
  const dict = useDictionary();
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // PENDING_APPROVAL only ever moves forward via PATCH /approve (ADMIN only, see
  // AdminUserManagementController#approve) — a branch manager can't activate their own creation
  // through the generic suspend/reactivate toggle below, so they get a read-only status card
  // instead of an action they'd just get a 403 from.
  if (status === "PENDING_APPROVAL") {
    if (!isAdmin) {
      return (
        <ActionCard
          icon="clock"
          title={dict.team.statusButton.awaitingApproval}
          description={dict.team.statusButton.awaitingApprovalDescription}
          disabled
        />
      );
    }

    async function handleApprove() {
      setError(null);
      setLoading(true);
      try {
        const res = await fetch(`/api/admin-users/${userId}/approve`, { method: "PATCH" });
        if (!res.ok) {
          const body = await res.json().catch(() => null);
          setError(body?.message ?? dict.team.statusButton.failedToApprove);
          return;
        }
        setSucceeded(true);
        setTimeout(() => router.refresh(), 500);
      } catch {
        setError(dict.common.unableToReachServer);
      } finally {
        setLoading(false);
      }
    }

    return (
      <div className="flex flex-col gap-2">
        <ActionCard
          icon={succeeded ? "check-circle" : "check-circle"}
          title={succeeded ? dict.team.statusButton.done : dict.team.statusButton.approveAccount}
          description={dict.team.statusButton.approveDescription}
          disabled={succeeded || loading}
          onClick={handleApprove}
        />
        {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
      </div>
    );
  }

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
