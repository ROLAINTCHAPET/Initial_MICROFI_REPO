"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Modal } from "@/components/Modal";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { COUNTRY_CODES } from "@/lib/countryCodes";

export function CreateBranchModal() {
  const router = useRouter();
  const dict = useDictionary();
  const [open, setOpen] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [countryCode, setCountryCode] = useState(COUNTRY_CODES[0].code);
  const [localNumber, setLocalNumber] = useState("");
  // Not user-editable: every CEMAC member state shares the same UTC+1 offset with no DST, so
  // there's nothing meaningful to choose between — one fixed IANA zone covers every branch.
  const timezone = "Africa/Douala";
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/branches", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code, name, phone: localNumber.trim() ? `${countryCode}${localNumber.trim()}` : undefined, timezone }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.branches.createModal.failedToCreate);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setOpen(false);
        setSucceeded(false);
        setCode("");
        setName("");
        setLocalNumber("");
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
      <Button onClick={() => setOpen(true)}>
        <Icon name="plus" className="size-5" />
        {dict.branches.createModal.title}
      </Button>
      <Modal open={open} onClose={() => setOpen(false)} title={dict.branches.createModal.title}>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Input label={dict.branches.createModal.codeLabel} name="code" value={code} onChange={(e) => setCode(e.target.value)} required />
          <Input label={dict.branches.createModal.nameLabel} name="name" value={name} onChange={(e) => setName(e.target.value)} required />
          <div className="flex flex-col gap-1">
            <label htmlFor="branch-phone" className="text-base font-semibold text-on-surface">
              {dict.branches.createModal.phoneLabel}
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
                id="branch-phone"
                type="tel"
                inputMode="numeric"
                placeholder="600000000"
                value={localNumber}
                onChange={(e) => setLocalNumber(e.target.value.replace(/\D/g, ""))}
                className="flex-1 min-h-12 px-3 rounded-[var(--radius-sm)] outline-none text-base bg-surface-container-lowest border-2 border-outline-variant focus-visible:border-primary"
              />
            </div>
          </div>
          {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
          <div className="flex justify-end gap-2 mt-2">
            <Button type="button" variant="ghost" onClick={() => setOpen(false)} disabled={succeeded}>
              {dict.common.cancel}
            </Button>
            <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded}>
              {succeeded ? (
                <>
                  <Icon name="check-circle" className="size-5" />
                  {dict.branches.createModal.created}
                </>
              ) : (
                dict.branches.createModal.submit
              )}
            </Button>
          </div>
        </form>
      </Modal>
    </>
  );
}
