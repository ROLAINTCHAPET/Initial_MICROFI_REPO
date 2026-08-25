"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Input } from "@/components/Input";
import { TimePicker } from "@/components/TimePicker";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";

// Shared by BranchSettingsModal (ADMIN, editing any branch from the Branch Directory) and the
// BRANCH_MANAGER's own /settings page — one edit surface for everything a branch controls about
// itself, including working hours.
export function BranchSettingsForm({
  branchId,
  openTime,
  closeTime,
  openTimeLocked,
  phone,
  maxCashiers,
  requireImei,
  defaultCeilingPct,
  onCancel,
  onSaved,
}: {
  branchId: string;
  openTime: string | null;
  closeTime: string | null;
  openTimeLocked: boolean;
  phone: string | null;
  maxCashiers: number;
  requireImei: boolean;
  defaultCeilingPct: number;
  onCancel?: () => void;
  onSaved?: () => void;
}) {
  const router = useRouter();
  const dict = useDictionary();
  const [openTimeValue, setOpenTimeValue] = useState(openTime?.slice(0, 5) ?? "08:00");
  const [closeTimeValue, setCloseTimeValue] = useState(closeTime?.slice(0, 5) ?? "17:00");
  const [phoneValue, setPhoneValue] = useState(phone ?? "");
  const [maxCashiersValue, setMaxCashiersValue] = useState(String(maxCashiers));
  const [requireImeiValue, setRequireImeiValue] = useState(requireImei);
  const [defaultCeilingPctValue, setDefaultCeilingPctValue] = useState(String(defaultCeilingPct));
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const requests: Promise<Response>[] = [
        fetch(`/api/branches/${branchId}/schedule`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ openTime: `${openTimeValue}:00`, closeTime: `${closeTimeValue}:00` }),
        }),
      ];
      if (phoneValue.trim()) {
        requests.push(
          fetch(`/api/branches/${branchId}/phone`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ phone: phoneValue.trim() }),
          })
        );
      }
      if (Number(maxCashiersValue) !== maxCashiers) {
        requests.push(
          fetch(`/api/branches/${branchId}/max-cashiers`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ maxCashiers: Number(maxCashiersValue) }),
          })
        );
      }
      if (requireImeiValue !== requireImei) {
        requests.push(
          fetch(`/api/branches/${branchId}/require-imei`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ requireImei: requireImeiValue }),
          })
        );
      }
      if (Number(defaultCeilingPctValue) !== defaultCeilingPct) {
        requests.push(
          fetch(`/api/branches/${branchId}/default-ceiling-pct`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ defaultCeilingPct: Number(defaultCeilingPctValue) }),
          })
        );
      }
      const results = await Promise.all(requests);
      const failed = results.find((res) => !res.ok);
      if (failed) {
        const body = await failed.json().catch(() => null);
        setError(body?.message ?? dict.branches.settingsForm.failedToSave);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setSucceeded(false);
        router.refresh();
        onSaved?.();
      }, 600);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4">
        <div>
          <TimePicker label={dict.branches.settingsForm.openTimeLabel} name="openTime" value={openTimeValue} onChange={setOpenTimeValue} disabled={openTimeLocked} />
          {openTimeLocked && (
            <p className="text-xs text-on-surface-variant mt-1">{dict.branches.settingsForm.openTimeLockedNote}</p>
          )}
        </div>
        <TimePicker label={dict.branches.settingsForm.closeTimeLabel} name="closeTime" value={closeTimeValue} onChange={setCloseTimeValue} />
      </div>
      <p className="text-xs text-on-surface-variant -mt-2">{dict.branches.settingsForm.closeTimeHint}</p>
      <div>
        <Input
          label={dict.dashboard.contactNumber}
          name="phone"
          type="tel"
          icon={<Icon name="phone" className="size-5" />}
          placeholder="+237600000000"
          value={phoneValue}
          onChange={(e) => setPhoneValue(e.target.value)}
        />
        <p className="text-xs text-on-surface-variant mt-1">{dict.branches.settingsForm.contactHint}</p>
      </div>
      <div>
        <Input
          label={dict.branches.settingsForm.cashierCapLabel}
          name="maxCashiers"
          type="number"
          min={1}
          icon={<Icon name="account-balance-wallet" className="size-5" />}
          value={maxCashiersValue}
          onChange={(e) => setMaxCashiersValue(e.target.value)}
          required
        />
        <p className="text-xs text-on-surface-variant mt-1">{dict.branches.settingsForm.cashierCapHint}</p>
      </div>
      <div>
        <Input
          label={dict.branches.settingsForm.defaultCeilingLabel}
          name="defaultCeilingPct"
          type="number"
          min={1}
          icon={<Icon name="lock" className="size-5" />}
          value={defaultCeilingPctValue}
          onChange={(e) => setDefaultCeilingPctValue(e.target.value)}
          required
        />
        <p className="text-xs text-on-surface-variant mt-1">
          {dict.branches.settingsForm.defaultCeilingHint}
        </p>
      </div>
      <div className="flex items-start gap-3 p-3 rounded-[var(--radius-sm)] border-2 border-outline-variant">
        <input
          id="require-imei"
          type="checkbox"
          checked={requireImeiValue}
          onChange={(e) => setRequireImeiValue(e.target.checked)}
          className="mt-0.5 size-4 cursor-pointer accent-primary"
        />
        <label htmlFor="require-imei" className="cursor-pointer">
          <p className="text-sm font-semibold text-on-surface">{dict.branches.settingsForm.requireImeiLabel}</p>
          <p className="text-xs text-on-surface-variant mt-1">
            {dict.branches.settingsForm.requireImeiHint}
          </p>
        </label>
      </div>
      {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
      <div className="flex justify-end gap-2 mt-2">
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel} disabled={succeeded}>
            {dict.common.cancel}
          </Button>
        )}
        <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded}>
          {succeeded ? (
            <>
              <Icon name="check-circle" className="size-5" />
              {dict.branches.settingsForm.saved}
            </>
          ) : (
            dict.branches.settingsForm.saveSettings
          )}
        </Button>
      </div>
    </form>
  );
}
