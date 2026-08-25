"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { Modal } from "@/components/Modal";
import { Icon } from "@/components/Icon";
import type { RegistrationApplicationResponse } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

export function ApproveButton({ applicationId, login, targetRole }: { applicationId: string; login: string; targetRole: string }) {
  const router = useRouter();
  const dict = useDictionary();
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
        setError(body && "message" in body ? String((body as unknown as { message?: string }).message) : dict.registrations.approve.failedToApprove);
        return;
      }
      setCredentials({ tempPassword: body?.tempPassword ?? "", tempPin: body?.tempPin ?? null });
    } catch {
      setError(dict.common.unableToReachServer);
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
        {dict.registrations.approve.button}
      </Button>
      {error && <p role="alert" className="text-xs text-danger-red">{error}</p>}

      <Modal open={credentials !== null} onClose={closeAndRefresh} title={dict.registrations.approve.modalTitle}>
        <div className="flex flex-col gap-4">
          <p className="text-sm text-on-surface">
            {t(dict.registrations.approve.provisionedIntro, {
              role: targetRole === "AGENT" ? dict.registrations.approve.roleAgent : dict.registrations.approve.roleAccount,
            })}
            <strong>{dict.registrations.approve.onlyTime}</strong>
            {dict.registrations.approve.provisionedOutro}
          </p>
          <div className="flex flex-col gap-2 p-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest font-mono text-sm">
            <p><span className="text-on-surface-variant">{dict.registrations.approve.usernameLabel}</span> {login}</p>
            <p><span className="text-on-surface-variant">{dict.registrations.approve.tempPasswordLabel}</span> {credentials?.tempPassword}</p>
            {credentials?.tempPin && <p><span className="text-on-surface-variant">{dict.registrations.approve.tempPinLabel}</span> {credentials.tempPin}</p>}
          </div>
          <Button onClick={closeAndRefresh}>{dict.registrations.approve.done}</Button>
        </div>
      </Modal>
    </div>
  );
}
