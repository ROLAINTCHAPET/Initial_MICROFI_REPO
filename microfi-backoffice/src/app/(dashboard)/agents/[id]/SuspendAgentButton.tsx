"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import type { AgentStatus } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function SuspendAgentButton({ agentId, status, hasCeiling }: { agentId: string; status: AgentStatus; hasCeiling: boolean }) {
  const router = useRouter();
  const dict = useDictionary();
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reactivation is only offered from SUSPENDED — a PENDING_CEILING agent (fresh enrollment,
  // never yet active) activates automatically once its escrow ceiling is funded, never via this
  // button. See AgentManagementController#updateStatus.
  const isSuspendAction = status !== "SUSPENDED";
  const nextStatus: AgentStatus = isSuspendAction ? "SUSPENDED" : "ACTIVE";
  const reactivateDisabled = !isSuspendAction && !hasCeiling;

  async function handleClick() {
    if (isSuspendAction && !confirm(dict.agents.suspend.confirmSuspend)) {
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/agents/${agentId}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: nextStatus }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.agents.suspend.failedToUpdateStatus);
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
    <div className="flex flex-col gap-2 items-start">
      <Button
        variant={succeeded ? "success" : isSuspendAction ? "danger" : "success"}
        loading={loading}
        disabled={succeeded || reactivateDisabled}
        onClick={handleClick}
      >
        {succeeded ? (
          <>
            <Icon name="check-circle" className="size-5" />
            {dict.agents.suspend.done}
          </>
        ) : isSuspendAction ? (
          dict.agents.suspend.suspendAgent
        ) : (
          dict.agents.suspend.reactivateAgent
        )}
      </Button>
      {reactivateDisabled && !succeeded && (
        <p className="text-xs text-on-surface-variant max-w-[220px]">{dict.agents.suspend.fundBeforeReactivate}</p>
      )}
      {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
    </div>
  );
}
