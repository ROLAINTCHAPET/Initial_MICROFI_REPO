"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { ErrorDialog } from "@/components/ErrorDialog";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function DeleteAdminUserModal({ userId }: { userId: string }) {
  const router = useRouter();
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/admin-users/${userId}/delete`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.team.deleteAccount.failedToDelete);
        return;
      }
      setOpen(false);
      setReason("");
      router.push("/team");
      router.refresh();
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Button variant="danger" onClick={() => setOpen(true)}>
        <Icon name="warning" className="size-5" />
        {dict.team.deleteAccount.button}
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title={dict.team.deleteAccount.modalTitle}>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <p className="text-sm text-on-surface-variant">{dict.team.deleteAccount.warning}</p>
          <div className="flex flex-col gap-1">
            <label htmlFor="delete-reason" className="text-sm font-semibold text-on-surface">
              {dict.team.deleteAccount.reasonLabel}
            </label>
            <textarea
              id="delete-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              required
              rows={4}
              className="w-full p-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm outline-none focus-visible:border-primary"
              placeholder={dict.team.deleteAccount.reasonPlaceholder}
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" variant="danger" loading={loading}>
              {dict.team.deleteAccount.confirm}
            </Button>
          </div>
        </form>
      </Modal>
      <ErrorDialog open={error !== null} message={error} onClose={() => setError(null)} title={dict.team.deleteAccount.deletionFailedTitle} />
    </>
  );
}
