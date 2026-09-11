"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/Button";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";
import type { AdminRole, BranchResponse } from "@/lib/types";

type Audience = "CLIENTS" | "AGENTS";
type Scope = "OWN_BRANCH" | "NETWORK_WIDE" | "ONE_BRANCH";

export function BroadcastComposer({
  role,
  branches,
  ownBranchLabel,
}: {
  role: AdminRole;
  branches: BranchResponse[];
  ownBranchLabel: string | null;
}) {
  const router = useRouter();
  const dict = useDictionary();
  const [audience, setAudience] = useState<Audience>("CLIENTS");
  // ADMIN defaults to network-wide (the capability only ADMIN has); BRANCH_MANAGER has no choice
  // here at all — the backend forces their own branch regardless of what's sent.
  const [scope, setScope] = useState<Scope>("NETWORK_WIDE");
  const [branchId, setBranchId] = useState<string>(branches[0]?.id ?? "");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSuccess(null);
    setLoading(true);
    try {
      const res = await fetch("/api/broadcasts", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          audience,
          branchId: role === "ADMIN" ? (scope === "ONE_BRANCH" ? branchId : null) : null,
          message: message.trim(),
        }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.broadcasts.failedToSend);
        return;
      }
      const created = await res.json();
      setSuccess(t(dict.broadcasts.sent, { count: created.recipientCount ?? 0 }));
      setMessage("");
      router.refresh();
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant p-5 flex flex-col gap-4"
    >
      <h3 className="text-h2 text-primary">{dict.broadcasts.composeTitle}</h3>

      <div className="flex flex-col gap-2">
        <label className="text-sm font-semibold text-on-surface">{dict.broadcasts.audienceLabel}</label>
        <div className="flex gap-2">
          {(["CLIENTS", "AGENTS"] as Audience[]).map((a) => (
            <button
              key={a}
              type="button"
              onClick={() => setAudience(a)}
              className={`px-3 py-1.5 rounded-[var(--radius-full)] text-xs font-bold transition-colors ${
                audience === a ? "bg-primary text-on-primary" : "border-2 border-outline-variant text-primary hover:bg-surface-container-low"
              }`}
            >
              {a === "CLIENTS" ? dict.broadcasts.audienceClients : dict.broadcasts.audienceAgents}
            </button>
          ))}
        </div>
      </div>

      {role === "ADMIN" ? (
        <div className="flex flex-col gap-2">
          <label className="text-sm font-semibold text-on-surface">{dict.broadcasts.scopeLabel}</label>
          <div className="flex flex-wrap items-center gap-2">
            {(["NETWORK_WIDE", "ONE_BRANCH"] as Scope[]).map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setScope(s)}
                className={`px-3 py-1.5 rounded-[var(--radius-full)] text-xs font-bold transition-colors ${
                  scope === s ? "bg-primary text-on-primary" : "border-2 border-outline-variant text-primary hover:bg-surface-container-low"
                }`}
              >
                {s === "NETWORK_WIDE" ? dict.broadcasts.scopeNetworkWide : dict.broadcasts.scopeOneBranch}
              </button>
            ))}
            {scope === "ONE_BRANCH" && (
              <select
                value={branchId}
                onChange={(e) => setBranchId(e.target.value)}
                className="h-9 px-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface-container-lowest text-sm cursor-pointer focus:outline-none focus:border-primary transition-colors"
              >
                {branches.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name} ({b.code})
                  </option>
                ))}
              </select>
            )}
          </div>
        </div>
      ) : (
        <p className="text-xs text-on-surface-variant">
          {dict.broadcasts.scopeLabel}: {ownBranchLabel ?? dict.broadcasts.scopeOwnBranch}
        </p>
      )}

      <div className="flex flex-col gap-2">
        <label htmlFor="broadcast-message" className="text-sm font-semibold text-on-surface">
          {dict.broadcasts.messageLabel}
        </label>
        <textarea
          id="broadcast-message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          rows={3}
          required
          placeholder={dict.broadcasts.messagePlaceholder}
          className="w-full px-3 py-2 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface text-sm focus:outline-none focus:border-primary transition-colors resize-none"
        />
      </div>

      {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
      {success && <p className="text-sm text-secondary font-medium">{success}</p>}

      <div className="flex justify-end">
        <Button type="submit" loading={loading} disabled={!message.trim() || (role === "ADMIN" && scope === "ONE_BRANCH" && !branchId)}>
          {loading ? dict.broadcasts.sending : dict.broadcasts.sendButton}
        </Button>
      </div>
    </form>
  );
}
