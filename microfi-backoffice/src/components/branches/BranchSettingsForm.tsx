"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Input } from "@/components/Input";
import { TimePicker } from "@/components/TimePicker";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { COUNTRY_CODES } from "@/lib/countryCodes";

// The stored phone is a single "+237600000000" string with no separator — split off whichever
// CEMAC calling code it starts with so the country selector and local-number field can be edited
// independently, same as CreateBranchModal's fresh-entry version of this same split.
function splitPhone(phone: string | null): { countryCode: string; localNumber: string } {
  if (!phone) return { countryCode: COUNTRY_CODES[0].code, localNumber: "" };
  const match = COUNTRY_CODES.find((c) => phone.startsWith(c.code));
  return match ? { countryCode: match.code, localNumber: phone.slice(match.code.length) } : { countryCode: COUNTRY_CODES[0].code, localNumber: phone };
}

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
  const initialPhone = splitPhone(phone);
  const [countryCode, setCountryCode] = useState(initialPhone.countryCode);
  const [localNumber, setLocalNumber] = useState(initialPhone.localNumber);
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
      if (localNumber.trim()) {
        requests.push(
          fetch(`/api/branches/${branchId}/phone`, {
            method: "PATCH",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ phone: `${countryCode}${localNumber.trim()}` }),
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
      <div className="flex flex-col gap-1">
        <label htmlFor="branch-settings-phone" className="text-base font-semibold text-on-surface">
          {dict.dashboard.contactNumber}
        </label>
        <div className="flex gap-2">
          <select
            aria-label={dict.registrations.newForm.countryCodeAriaLabel}
            value={countryCode}
            onChange={(e) => setCountryCode(e.target.value)}
            className="min-h-12 px-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-base shrink-0"
          >
            {COUNTRY_CODES.map((c) => (
              <option key={c.code} value={c.code}>
                {c.flag} {c.code}
              </option>
            ))}
          </select>
          <input
            id="branch-settings-phone"
            type="tel"
            inputMode="numeric"
            placeholder="600000000"
            value={localNumber}
            onChange={(e) => setLocalNumber(e.target.value.replace(/\D/g, ""))}
            className="flex-1 min-h-12 px-3 rounded-[var(--radius-sm)] outline-none text-base bg-surface-container-lowest border-2 border-outline-variant focus-visible:border-primary"
          />
        </div>
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
