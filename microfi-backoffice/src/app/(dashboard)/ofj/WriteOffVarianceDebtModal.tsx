"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function WriteOffVarianceDebtModal({ debtId, agentLabel, amountXaf }: { debtId: string; agentLabel: string; amountXaf: number }) {
  const router = useRouter();
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [proof, setProof] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  function close() {
    setOpen(false);
    setError(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!proof) {
      setError(dict.ofj.writeOff.uploadProofRequired);
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const formData = new FormData();
      formData.append("metadata", new Blob([JSON.stringify({ reason: reason.trim() })], { type: "application/json" }));
      formData.append("proof", proof);
      const res = await fetch(`/api/variance-debts/${debtId}/write-off`, {
        method: "PATCH",
        body: formData,
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.ofj.writeOff.failedToWriteOff);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setOpen(false);
        setSucceeded(false);
        setReason("");
        setProof(null);
        router.refresh();
      }, 600);
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
        <Icon name="trash" className="size-3.5" />
        {dict.ofj.writeOff.buttonLabel}
      </button>

      <Modal open={open} onClose={close} title={dict.ofj.writeOff.modalTitle}>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex gap-3 items-start bg-error-container/40 border-2 border-error/30 rounded-[var(--radius-sm)] p-4">
            <Icon name="warning" filled className="size-5 text-danger-red shrink-0 mt-0.5" />
            <p className="text-sm text-on-error-container">
              {dict.ofj.writeOff.warningPrefix}
              <span className="font-semibold tabular-nums">{amountXaf.toLocaleString()} XAF</span>
              {dict.ofj.writeOff.warningMiddle}
              <span className="font-semibold">{agentLabel}</span>
              {dict.ofj.writeOff.warningSuffix}
            </p>
          </div>

          <div className="flex flex-col gap-2">
            <label htmlFor="write-off-reason" className="text-sm font-semibold text-on-surface">
              {dict.ofj.writeOff.reasonLabel} <span className="text-danger-red">*</span>
            </label>
            <textarea
              id="write-off-reason"
              name="reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              required
              placeholder={dict.ofj.writeOff.reasonPlaceholder}
              className="w-full px-3 py-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm focus:outline-none focus:border-primary transition-colors resize-none"
            />
          </div>

          <div className="flex flex-col gap-2">
            <label htmlFor="write-off-proof" className="text-sm font-semibold text-on-surface">
              {dict.ofj.writeOff.proofLabel} <span className="text-danger-red">*</span>
            </label>
            <input
              id="write-off-proof"
              type="file"
              aria-label={dict.ofj.writeOff.proofAriaLabel}
              accept="application/pdf,image/jpeg"
              required
              onChange={(e) => setProof(e.target.files?.[0] ?? null)}
              className="w-full text-sm text-on-surface-variant file:mr-3 file:h-10 file:px-3 file:rounded-[var(--radius-sm)] file:border file:border-outline-variant file:bg-surface-container-lowest file:text-sm file:font-semibold file:cursor-pointer file:text-primary"
            />
            <p className="text-xs text-on-surface-variant">{dict.ofj.writeOff.proofHelp}</p>
          </div>

          {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}

          <div className="flex justify-end gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={close} disabled={succeeded}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" variant={succeeded ? "success" : "danger"} loading={loading} disabled={succeeded}>
              {succeeded ? (
                <>
                  <Icon name="check-circle" className="size-5" />
                  {dict.ofj.writeOff.writtenOff}
                </>
              ) : loading ? (
                dict.ofj.writeOff.writingOff
              ) : (
                <>
                  <Icon name="trash" className="size-4" />
                  {dict.ofj.writeOff.confirmButton}
                </>
              )}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  );
}
