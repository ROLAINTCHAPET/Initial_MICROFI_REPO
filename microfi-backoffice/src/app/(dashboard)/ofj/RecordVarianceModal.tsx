"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";

export function RecordVarianceModal({
  branchId,
  ofjAgentLineId,
  agentLabel,
  shortageXaf,
}: {
  branchId: string;
  ofjAgentLineId: string;
  agentLabel: string;
  shortageXaf: number;
}) {
  const router = useRouter();
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const [comment, setComment] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/ofj/${branchId}/variance`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ofjAgentLineId, comment: comment.trim() || undefined }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.ofj.recordVariance.failedToRecord);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setOpen(false);
        setSucceeded(false);
        setComment("");
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
        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[var(--radius-full)] border-2 border-danger-red text-danger-red text-xs font-bold cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:bg-danger-red/10 hover:scale-[1.03] active:scale-95"
      >
        <Icon name="warning" filled className="size-3.5" />
        {dict.ofj.recordVariance.buttonLabel}
      </button>

      <Modal open={open} onClose={() => setOpen(false)} title={dict.ofj.recordVariance.modalTitle}>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="flex gap-3 items-start bg-error-container/40 border-2 border-error/30 rounded-[var(--radius-sm)] p-4">
            <Icon name="warning" filled className="size-5 text-danger-red shrink-0 mt-0.5" />
            <p className="text-sm text-on-error-container">
              {dict.ofj.recordVariance.warningPrefix}
              <span className="font-semibold">{agentLabel}</span>
              {dict.ofj.recordVariance.warningMiddle}
              <span className="font-semibold tabular-nums">{shortageXaf.toLocaleString()} XAF</span>
              {dict.ofj.recordVariance.warningSuffix}
            </p>
          </div>

          <div className="flex flex-col gap-2">
            <label htmlFor="variance-comment" className="text-sm font-semibold text-on-surface">
              {dict.ofj.recordVariance.commentLabel}{" "}
              <span className="text-on-surface-variant font-normal">{dict.ofj.recordVariance.optionalSuffix}</span>
            </label>
            <textarea
              id="variance-comment"
              name="comment"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={3}
              placeholder={dict.ofj.recordVariance.commentPlaceholder}
              className="w-full px-3 py-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm focus:outline-none focus:border-primary transition-colors resize-none"
            />
          </div>

          {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}

          <div className="flex justify-end gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)} disabled={succeeded}>
              {dict.common.cancel}
            </Button>
            <button
              type="submit"
              disabled={loading || succeeded}
              className={`h-12 px-6 rounded-[var(--radius-md)] font-semibold text-sm cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-95 disabled:cursor-not-allowed disabled:opacity-70 flex items-center gap-2 ${
                succeeded ? "bg-success-emerald text-white" : "bg-danger-red text-white hover:bg-danger-red/90"
              }`}
            >
              {succeeded ? (
                <>
                  <Icon name="check-circle" className="size-5" />
                  {dict.ofj.recordVariance.recorded}
                </>
              ) : loading ? (
                dict.ofj.recordVariance.recording
              ) : (
                <>
                  <Icon name="warning" filled className="size-4" />
                  {dict.ofj.recordVariance.recordDebt}
                </>
              )}
            </button>
          </div>
        </form>
      </Modal>
    </>
  );
}
