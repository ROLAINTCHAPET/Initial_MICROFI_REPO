"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { ErrorDialog } from "@/components/ErrorDialog";

export function RejectApplicationModal({ applicationId }: { applicationId: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/registration-applications/${applicationId}/reject`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? "Failed to reject");
        return;
      }
      setOpen(false);
      setReason("");
      router.refresh();
    } catch {
      setError("Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <Button variant="danger" onClick={() => setOpen(true)}>
        <Icon name="warning" className="size-5" />
        Reject
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title="Reject Application">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label htmlFor="reject-reason" className="text-sm font-semibold text-on-surface">
              Reason
            </label>
            <textarea
              id="reject-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              required
              rows={4}
              className="w-full p-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm outline-none focus-visible:border-primary"
              placeholder="e.g. Criminal record scan illegible, resubmission required"
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="danger" loading={loading}>
              Reject Application
            </Button>
          </div>
        </form>
      </Modal>
      <ErrorDialog open={error !== null} message={error} onClose={() => setError(null)} title="Rejection Failed" />
    </>
  );
}
