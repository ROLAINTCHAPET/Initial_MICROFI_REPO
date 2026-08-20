"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Icon } from "@/components/Icon";
import type { ScheduleDefaultsResponse } from "@/lib/types";

export function GlobalThresholds({
  initial,
  editable,
  onSaveStart,
  onSaveSettled,
}: {
  initial: ScheduleDefaultsResponse;
  editable: boolean;
  onSaveStart?: () => void;
  onSaveSettled?: () => void;
}) {
  const router = useRouter();
  const [openTime, setOpenTime] = useState(initial.openTime.slice(0, 5));
  const [closeTime, setCloseTime] = useState(initial.closeTime.slice(0, 5));
  const [overrideAll, setOverrideAll] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleSaveClick() {
    if (overrideAll) {
      setConfirmOpen(true);
      return;
    }
    performSave();
  }

  async function performSave() {
    setConfirmOpen(false);
    setError(null);
    setLoading(true);
    onSaveStart?.();
    try {
      const res = await fetch(`/api/branches/schedule-defaults?overrideAll=${overrideAll}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ openTime: `${openTime}:00`, closeTime: `${closeTime}:00` }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? "Failed to save global defaults");
        onSaveSettled?.();
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setSucceeded(false);
        router.refresh();
        onSaveSettled?.();
      }, 700);
    } catch {
      setError("Unable to reach the server");
      onSaveSettled?.();
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-6">
      <div className="flex items-center gap-3 mb-6 pb-4 border-b-2 border-outline-variant">
        <Icon name="public" className="text-secondary size-8" />
        <div>
          <h2 className="text-h1 text-primary">Global Thresholds</h2>
          <p className="text-xs text-on-surface-variant">
            {overrideAll
              ? "Will overwrite every branch's hours, including ones already configured"
              : "Applied to every branch that hasn't set its own hours — per-branch overrides always win"}
            {initial.updatedAt && <> &middot; last updated {new Date(initial.updatedAt).toLocaleString()}</>}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 items-end">
        <div className="flex flex-col gap-2">
          <label className="text-sm font-semibold text-on-surface">Default Open Time</label>
          <div className="relative">
            <Icon name="schedule" className="absolute left-3 top-1/2 -translate-y-1/2 text-outline size-5 pointer-events-none" />
            <input
              type="time"
              value={openTime}
              disabled={!editable}
              onChange={(e) => setOpenTime(e.target.value)}
              className="w-full h-12 pl-10 pr-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm text-primary cursor-pointer focus:outline-none focus:border-primary disabled:text-text-grey-disabled disabled:bg-surface-container-low disabled:cursor-not-allowed"
            />
          </div>
        </div>
        <div className="flex flex-col gap-2">
          <label className="text-sm font-semibold text-on-surface">Default Close Time</label>
          <div className="relative">
            <Icon name="schedule" className="absolute left-3 top-1/2 -translate-y-1/2 text-outline size-5 pointer-events-none" />
            <input
              type="time"
              value={closeTime}
              disabled={!editable}
              onChange={(e) => setCloseTime(e.target.value)}
              className="w-full h-12 pl-10 pr-4 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm text-primary cursor-pointer focus:outline-none focus:border-primary disabled:text-text-grey-disabled disabled:bg-surface-container-low disabled:cursor-not-allowed"
            />
          </div>
        </div>
        {editable && (
          <button
            onClick={handleSaveClick}
            disabled={loading || succeeded}
            title={overrideAll ? "Save and overwrite every branch's hours" : "Save and apply to every branch without its own hours"}
            className={`h-12 px-6 font-semibold text-sm rounded-[var(--radius-md)] flex items-center justify-center gap-2 cursor-pointer transition-[background-color,transform,box-shadow] duration-150 ease-out hover:scale-[1.04] hover:shadow-[var(--shadow-elevation-1)] active:scale-[0.95] disabled:cursor-not-allowed disabled:hover:scale-100 disabled:active:scale-100 disabled:opacity-60 ${
              succeeded
                ? "bg-success-emerald text-white success-pop"
                : overrideAll
                  ? "bg-danger-red text-white hover:bg-danger-red/90"
                  : "bg-primary text-on-primary hover:bg-primary/90"
            }`}
          >
            {succeeded ? (
              <>
                <Icon name="check-circle" className="size-5" />
                Saved
              </>
            ) : loading ? (
              "Saving…"
            ) : overrideAll ? (
              "Apply to All Branches"
            ) : (
              "Save Global Changes"
            )}
          </button>
        )}
      </div>

      {editable && (
        <label className="mt-4 flex items-center gap-2.5 cursor-pointer select-none w-fit">
          <input
            type="checkbox"
            checked={overrideAll}
            onChange={(e) => setOverrideAll(e.target.checked)}
            className="h-5 w-5 rounded-[var(--radius-sm)] border-2 border-outline-variant text-danger-red accent-danger-red cursor-pointer"
          />
          <span className="text-sm text-on-surface-variant">
            Apply to <span className="font-semibold text-on-surface">all</span> branches, overriding hours they&apos;ve already set
          </span>
        </label>
      )}

      {error && <p role="alert" className="text-sm text-danger-red mt-3">{error}</p>}

      <Modal open={confirmOpen} onClose={() => setConfirmOpen(false)} title="Overwrite every branch's hours?">
        <div className="flex flex-col gap-4">
          <div className="flex gap-3 items-start bg-error-container/40 border-2 border-error/30 rounded-[var(--radius-sm)] p-4">
            <Icon name="warning" filled className="size-5 text-danger-red shrink-0 mt-0.5" />
            <p className="text-sm text-on-error-container">
              This sets <span className="font-semibold">every branch</span> — including branches that already have their own working
              hours — to <span className="font-semibold tabular-nums">{openTime} – {closeTime}</span>. Any custom hours a branch
              manager configured will be lost.
            </p>
          </div>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setConfirmOpen(false)}
              className="h-12 px-6 rounded-[var(--radius-md)] border-2 border-outline-variant text-primary font-semibold text-sm cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:bg-surface-container-low hover:scale-[1.03] active:scale-95"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={performSave}
              className="h-12 px-6 rounded-[var(--radius-md)] bg-danger-red text-white font-semibold text-sm cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:bg-danger-red/90 hover:scale-[1.03] active:scale-95 flex items-center gap-2"
            >
              <Icon name="warning" filled className="size-4" />
              Yes, overwrite all branches
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
