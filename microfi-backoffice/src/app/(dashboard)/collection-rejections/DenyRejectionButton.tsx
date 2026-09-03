"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/Button";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function DenyRejectionButton({ requestId }: { requestId: string }) {
  const router = useRouter();
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  function close() {
    setOpen(false);
    setError(null);
    setReason("");
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/collection-rejection-requests/${requestId}/deny`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: reason.trim() }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.collectionRejections.failedToDeny);
        return;
      }
      close();
      router.refresh();
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[var(--radius-full)] border-2 border-outline-variant text-primary text-xs font-bold cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:bg-surface-container-low hover:scale-[1.03] active:scale-95"
      >
        {dict.collectionRejections.denyButton}
      </button>

      <Modal open={open} onClose={close} title={dict.collectionRejections.denyModalTitle}>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <label htmlFor="deny-reason" className="text-sm font-semibold text-on-surface">
              {dict.collectionRejections.denyReasonLabel} <span className="text-danger-red">*</span>
            </label>
            <textarea
              id="deny-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              required
              className="w-full px-3 py-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm focus:outline-none focus:border-primary transition-colors resize-none"
            />
          </div>

          {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}

          <div className="flex justify-end gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={close}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" variant="danger" loading={loading}>
              {dict.collectionRejections.confirmDeny}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  );
}
