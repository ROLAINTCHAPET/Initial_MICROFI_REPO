"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

const DENOMINATIONS = [10000, 5000, 2000, 1000, 500, 200, 100, 50, 25];

export interface QueueLine {
  lineId: string;
  agentId: string;
  agentLabel: string;
  digitalTotalXaf: number;
}

export interface ValidatedLine {
  lineId: string;
  agentLabel: string;
  physicalTotalXaf: number;
  deltaXaf: number;
}

export function ReconcileWorkspace({
  branchId,
  queue,
  validated,
}: {
  branchId: string;
  queue: QueueLine[];
  validated: ValidatedLine[];
}) {
  const router = useRouter();
  const dict = useDictionary();
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(queue[0]?.agentId ?? null);
  const [counts, setCounts] = useState<Record<number, number>>({});
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  const selected = queue.find((q) => q.agentId === selectedAgentId) ?? null;
  const physicalTotal = DENOMINATIONS.reduce((sum, denom) => sum + denom * (counts[denom] ?? 0), 0);
  const expected = selected?.digitalTotalXaf ?? 0;
  const delta = physicalTotal - expected;
  const isMatch = selected !== null && delta === 0;

  function selectAgent(agentId: string) {
    setSelectedAgentId(agentId);
    setCounts({});
    setError(null);
  }

  async function handleSubmit() {
    if (!selected) return;
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/ofj/${branchId}/reconcile`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          agentId: selected.agentId,
          physicalDenominationLines: DENOMINATIONS.map((faceValueXaf) => ({ faceValueXaf, quantity: counts[faceValueXaf] ?? 0 })),
        }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.cashier.reconcile.failedToReconcile);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setSucceeded(false);
        setCounts({});
        setSelectedAgentId(null);
        router.refresh();
      }, 600);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex flex-col lg:flex-row gap-6 max-w-7xl mx-auto w-full items-start">
      <div className="w-full lg:w-80 shrink-0 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b-2 border-outline-variant bg-surface-container-low">
          <h2 className="flex items-center gap-2 font-bold text-on-surface">
            <Icon name="history" className="size-5 text-primary" />
            {dict.cashier.reconcile.activeQueue}
          </h2>
          <span className="bg-primary-container text-on-primary rounded-full px-2 py-0.5 font-semibold text-xs">{t(dict.cashier.reconcile.waitingCount, { count: queue.length })}</span>
        </div>
        <div className="p-4 flex flex-col gap-3">
          {queue.map((item) => {
            const active = item.agentId === selectedAgentId;
            return (
              <button
                key={item.lineId}
                onClick={() => selectAgent(item.agentId)}
                className={`card-interactive cursor-pointer text-left bg-surface-container-lowest rounded-[var(--radius-md)] p-4 shadow-sm relative transition-colors ${
                  active ? "border-2 border-primary" : "border-2 border-outline-variant hover:border-primary/50"
                }`}
              >
                <p className={`font-semibold text-xs ${active ? "text-primary" : "text-on-surface-variant"}`}>{item.agentLabel}</p>
                <div className="flex items-center gap-2 mt-2 pt-2 border-t-2 border-outline-variant/50">
                  <Icon name="account-balance-wallet" className="size-5 text-outline" />
                  <span className={`text-h2 tabular-nums ${active ? "text-primary" : "text-on-surface"}`}>{item.digitalTotalXaf.toLocaleString()} XAF</span>
                </div>
              </button>
            );
          })}
          {queue.length === 0 && <p className="text-sm text-on-surface-variant p-2">{dict.cashier.reconcile.noAgentsPending}</p>}
        </div>
      </div>

      <div className="flex-1 min-w-0 w-full flex flex-col gap-4">
        {selected ? (
          <>
            <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-6 flex justify-between items-center">
              <div>
                <h2 className="font-bold text-lg text-primary">{selected.agentLabel}</h2>
              </div>
              <div className="text-right">
                <p className="font-semibold text-xs text-on-surface-variant uppercase tracking-wider mb-1">{dict.cashier.reconcile.expectedDeposit}</p>
                <p className="text-display text-primary">{expected.toLocaleString()} XAF</p>
              </div>
            </div>

            <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-6 flex flex-col">
              <h3 className="text-h2 text-primary mb-4 flex items-center gap-2">
                <Icon name="account-balance-wallet" className="size-6" />
                {dict.cashier.reconcile.physicalCashCount}
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-3">
                {DENOMINATIONS.map((denom) => {
                  const count = counts[denom] ?? 0;
                  const subtotal = denom * count;
                  return (
                    <div key={denom} className="flex items-center justify-between p-3 rounded-[var(--radius-sm)] border-2 border-outline-variant bg-surface">
                      <span className="font-semibold text-sm text-primary w-24">{denom.toLocaleString()} XAF</span>
                      <div className="flex items-center gap-3">
                        <span className="text-outline font-semibold text-xs">x</span>
                        <input
                          type="number"
                          min={0}
                          value={count || ""}
                          placeholder="0"
                          onChange={(e) => setCounts((prev) => ({ ...prev, [denom]: parseInt(e.target.value, 10) || 0 }))}
                          className="w-24 h-12 text-center font-bold text-lg rounded-[var(--radius-sm)] border-2 border-outline-variant focus:border-primary bg-surface-container-lowest cursor-text"
                        />
                        <span className={`font-semibold text-sm w-28 text-right ${subtotal > 0 ? "text-on-surface" : "text-outline"}`}>= {subtotal.toLocaleString()}</span>
                      </div>
                    </div>
                  );
                })}
              </div>

              <div className="mt-6 pt-6 border-t-2 border-outline-variant flex items-end justify-between flex-wrap gap-4">
                <div>
                  <p className="font-semibold text-xs text-on-surface-variant uppercase tracking-wider mb-1">{dict.cashier.reconcile.physicalCountTotal}</p>
                  <div className="flex items-center gap-4">
                    <p className="text-display text-primary">{physicalTotal.toLocaleString()} XAF</p>
                    {isMatch ? (
                      <span className="bg-secondary-container text-on-secondary-container px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1">
                        <Icon name="check-circle" className="size-4" />
                        {dict.cashier.reconcile.match}
                      </span>
                    ) : (
                      <span className="bg-error-container text-on-error-container px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1">
                        <Icon name="warning" className="size-4" />
                        {delta > 0 ? "+" : ""}
                        {delta.toLocaleString()} XAF
                      </span>
                    )}
                  </div>
                </div>
                <button
                  onClick={handleSubmit}
                  disabled={loading || succeeded}
                  className={`h-12 px-8 rounded-[var(--radius-md)] font-semibold text-sm flex items-center gap-2 cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98] disabled:cursor-not-allowed disabled:hover:scale-100 disabled:active:scale-100 disabled:opacity-80 ${
                    succeeded ? "bg-success-emerald text-white" : "bg-primary text-on-primary hover:bg-primary/90"
                  }`}
                >
                  <Icon name="check-circle" className="size-5" />
                  {succeeded ? dict.cashier.reconcile.posted : loading ? dict.cashier.reconcile.posting : dict.cashier.reconcile.verifyAndPost}
                </button>
              </div>
              {error && <p role="alert" className="text-sm text-danger-red mt-3">{error}</p>}
              {!isMatch && (
                <p className="text-xs text-on-surface-variant mt-2">
                  {dict.cashier.reconcile.shortageUnresolvedNotice}
                </p>
              )}
            </div>
          </>
        ) : (
          <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-10 flex flex-col items-center gap-2 text-center text-sm text-on-surface-variant">
            <Icon name="info" className="size-6 text-outline-variant" />
            {dict.cashier.reconcile.selectAgentPrompt}
          </div>
        )}
      </div>

      <div className="w-full lg:w-72 shrink-0 bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] overflow-hidden">
        <div className="px-5 py-4 border-b-2 border-outline-variant bg-surface-container-low">
          <h2 className="flex items-center gap-2 font-bold text-on-surface">
            <Icon name="check-circle" className="size-5 text-primary" />
            {dict.cashier.reconcile.todaysValidated}
          </h2>
        </div>
        <div className="p-4 flex flex-col gap-2">
          {validated.map((item) => (
            <div key={item.lineId} className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-sm)] p-3 flex flex-col gap-1">
              <span className="font-semibold text-xs text-on-surface-variant">{item.agentLabel}</span>
              <div className="flex justify-between items-center">
                <span className="font-semibold text-sm text-primary">{item.physicalTotalXaf.toLocaleString()} XAF</span>
                <Icon name="check-circle" className="size-5 text-secondary" />
              </div>
            </div>
          ))}
          {validated.length === 0 && <p className="text-sm text-on-surface-variant p-2">{dict.cashier.reconcile.noValidatedLinesYet}</p>}
        </div>
      </div>
    </div>
  );
}
