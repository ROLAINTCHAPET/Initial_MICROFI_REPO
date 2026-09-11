"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { PageHeader } from "@/components/PageHeaderContext";
import { BackLink } from "@/components/BackLink";
import { Input } from "@/components/Input";
import { Button } from "@/components/Button";
import type { AdminRole, BranchResponse } from "@/lib/types";
import { useDictionary } from "@/lib/i18n/I18nProvider";

// Red asterisk — the visual marker for every mandatory field on this form (Branch, CBS Account
// Number, Full Name, and Login/PIN once "set credentials now" is checked). Email and phone are
// optional and left unmarked.
function Req() {
  return (
    <span className="text-danger-red ml-0.5" aria-hidden>
      *
    </span>
  );
}

// The recovery case this exists for: a client already exists in the CBS but never made it into
// MICROFI's local mirror (an automatic refresh missed them, or there simply isn't one yet — see
// CreateClientRequest's javadoc). Login/PIN are optional here specifically so that case can be
// made immediately usable in one action instead of also waiting on separate self-activation.
export function NewClientForm({
  branches,
  callerRole,
  callerBranchId,
}: {
  branches: BranchResponse[];
  callerRole: AdminRole;
  callerBranchId: string | null;
}) {
  const router = useRouter();
  const dict = useDictionary();
  const selectableBranches = callerRole === "BRANCH_MANAGER" ? branches.filter((b) => b.id === callerBranchId) : branches;

  const [branchId, setBranchId] = useState(selectableBranches[0]?.id ?? "");
  const [mfiMemberNo, setMfiMemberNo] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [setCredentials, setSetCredentials] = useState(false);
  const [login, setLogin] = useState("");
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/clients", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          mfiMemberNo: mfiMemberNo.trim(),
          fullName: fullName.trim(),
          email: email.trim() || undefined,
          phone: phone.trim() || undefined,
          branchId,
          login: setCredentials ? login.trim() : undefined,
          pin: setCredentials ? pin.trim() : undefined,
        }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.clients.createModal.failedToCreate);
        return;
      }
      const client = await res.json();
      setSucceeded(true);
      setTimeout(() => {
        router.push(`/clients/${client.id}`);
        router.refresh();
      }, 600);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-2xl mx-auto w-full flex flex-col gap-6">
      <BackLink href="/clients" label={dict.clients.backToClients} />
      <PageHeader title={dict.clients.createModal.title} subtitle={dict.clients.createModal.recoveryHint} />

      <form onSubmit={handleSubmit} className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-6 flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="new-client-branch" className="text-base font-semibold text-on-surface">
            {dict.registrations.newForm.branchLabel}
            <Req />
          </label>
          <select
            id="new-client-branch"
            value={branchId}
            onChange={(e) => setBranchId(e.target.value)}
            disabled={selectableBranches.length === 1}
            className="min-h-12 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-base disabled:text-text-grey-disabled disabled:bg-surface-grey-100 w-full min-w-0 max-w-full"
            required
          >
            {selectableBranches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name} ({b.code})
              </option>
            ))}
          </select>
        </div>

        <Input
          label={<>{dict.clients.createModal.accountNumberLabel}<Req /></>}
          name="mfiMemberNo"
          value={mfiMemberNo}
          onChange={(e) => setMfiMemberNo(e.target.value)}
          required
        />
        <Input
          label={<>{dict.clients.createModal.nameLabel}<Req /></>}
          name="fullName"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          required
        />
        <Input
          label={dict.clients.createModal.emailLabel}
          name="email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Input label={dict.clients.createModal.phoneLabel} name="phone" value={phone} onChange={(e) => setPhone(e.target.value)} />

        <div className="flex items-start gap-3 p-3 rounded-[var(--radius-sm)] border-2 border-outline-variant">
          <input
            id="set-client-credentials"
            type="checkbox"
            checked={setCredentials}
            onChange={(e) => setSetCredentials(e.target.checked)}
            className="mt-0.5 size-4 cursor-pointer accent-primary"
          />
          <label htmlFor="set-client-credentials" className="cursor-pointer">
            <p className="text-sm font-semibold text-on-surface">{dict.clients.createModal.setCredentialsLabel}</p>
            <p className="text-xs text-on-surface-variant mt-1">{dict.clients.createModal.setCredentialsHint}</p>
          </label>
        </div>

        {setCredentials && (
          <div className="grid grid-cols-2 gap-4">
            <Input
              label={<>{dict.clients.createModal.loginLabel}<Req /></>}
              name="login"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              required
            />
            <Input
              label={<>{dict.clients.createModal.pinLabel}<Req /></>}
              name="pin"
              type="password"
              inputMode="numeric"
              value={pin}
              onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
              required
            />
          </div>
        )}

        {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}

        <div className="flex justify-end gap-2 mt-2">
          <Button type="button" variant="ghost" onClick={() => router.push("/clients")} disabled={succeeded}>
            {dict.common.cancel}
          </Button>
          <Button type="submit" variant={succeeded ? "success" : "primary"} loading={loading} disabled={succeeded || !branchId}>
            {succeeded ? dict.clients.createModal.created : dict.clients.createModal.submit}
          </Button>
        </div>
      </form>
    </div>
  );
}
