"use client";

import { useState } from "react";
import { Modal } from "@/components/Modal";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import { BranchSettingsForm } from "./BranchSettingsForm";

// ADMIN or that branch's own BRANCH_MANAGER — the same settings a branch controls about itself
// day to day: when it's open, how a field agent reaches it (e.g. "Contact Branch" in the mobile
// app), and how many cashiers it can hold.
export function BranchSettingsModal({
  branchId,
  branchName,
  openTime,
  closeTime,
  openTimeLocked,
  phone,
  maxCashiers,
  requireImei,
  defaultCeilingPct,
}: {
  branchId: string;
  branchName: string;
  openTime: string | null;
  closeTime: string | null;
  openTimeLocked: boolean;
  phone: string | null;
  maxCashiers: number;
  requireImei: boolean;
  defaultCeilingPct: number;
}) {
  const dict = useDictionary();
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        title={dict.branches.settingsModal.editTooltip}
        className="group h-10 w-10 flex items-center justify-center rounded-full bg-primary text-on-primary shrink-0 cursor-pointer shadow-[var(--shadow-elevation-1)] transition-[background-color,transform,box-shadow] duration-200 ease-out hover:scale-110 hover:shadow-[var(--shadow-elevation-2)] active:scale-95"
      >
        <Icon name="pencil" className="size-4 transition-transform duration-200 group-hover:-rotate-12" />
      </button>

      <Modal open={open} onClose={() => setOpen(false)} title={t(dict.branches.settingsModal.modalTitle, { name: branchName })}>
        {/* Remounts on every open with a fresh key so a stale edit from a previous open (or new
            props after a save elsewhere refreshed the page) never lingers in local form state. */}
        <BranchSettingsForm
          key={`${open}-${openTime}-${closeTime}-${phone}-${maxCashiers}-${requireImei}-${defaultCeilingPct}`}
          branchId={branchId}
          openTime={openTime}
          closeTime={closeTime}
          openTimeLocked={openTimeLocked}
          phone={phone}
          maxCashiers={maxCashiers}
          requireImei={requireImei}
          defaultCeilingPct={defaultCeilingPct}
          onCancel={() => setOpen(false)}
          onSaved={() => setOpen(false)}
        />
      </Modal>
    </>
  );
}
