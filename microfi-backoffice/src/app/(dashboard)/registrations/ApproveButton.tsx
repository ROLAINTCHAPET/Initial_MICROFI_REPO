"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { Modal } from "@/components/Modal";
import { Icon } from "@/components/Icon";
import type { RegistrationApplicationResponse } from "@/lib/types";

export function ApproveButton({ applicationId, login, targetRole }: { applicationId: string; login: string; targetRole: string }) {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [credentials, setCredentials] = useState<{ tempPassword: string; tempPin: string | null } | null>(null);

  async function handleApprove() {
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/registration-applications/${applicationId}/approve`, { method: "PATCH" });
      const body = (await res.json().catch(() => null)) as RegistrationApplicationResponse | null;
      if (!res.ok) {
        setError(body && "message" in body ? String((body as unknown as { message?: string }).message) : "Failed to approve");
        return;
      }
      setCredentials({ tempPassword: body?.tempPassword ?? "", tempPin: body?.tempPin ?? null });
    } catch {
      setError("Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }

  function closeAndRefresh() {
    setCredentials(null);
    router.refresh();
  }

  return (
    <div className="flex flex-col gap-1 items-start">
      <Button variant="success" loading={loading} onClick={handleApprove}>
        <Icon name="check-circle" className="size-5" />
        Approve
      </Button>
      {error && <p role="alert" className="text-xs text-danger-red">{error}</p>}

      <Modal open={credentials !== null} onClose={closeAndRefresh} title="Account Provisioned">
        <div className="flex flex-col gap-4">
          <p className="text-sm text-on-surface">
            The {targetRole === "AGENT" ? "agent" : "account"} was provisioned and an activation SMS was sent (best-effort — local
            environments without real carrier credentials won&apos;t actually deliver it). This is the <strong>only time</strong> the
            temporary credential is shown — it is never stored or retrievable again.
          </p>
          <div className="flex flex-col gap-2 p-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest font-mono text-sm">
            <p><span className="text-on-surface-variant">Username:</span> {login}</p>
            <p><span className="text-on-surface-variant">Temporary password:</span> {credentials?.tempPassword}</p>
            {credentials?.tempPin && <p><span className="text-on-surface-variant">Temporary PIN:</span> {credentials.tempPin}</p>}
          </div>
          <Button onClick={closeAndRefresh}>Done</Button>
        </div>
      </Modal>
    </div>
  );
}
