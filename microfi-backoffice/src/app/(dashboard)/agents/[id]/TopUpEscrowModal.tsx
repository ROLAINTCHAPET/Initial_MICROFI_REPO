"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/Icon";

export function TopUpEscrowModal({ agentId, isPendingCeiling }: { agentId: string; isPendingCeiling: boolean }) {
  const router = useRouter();
  const [isOpen, setIsOpen] = useState(false);
  const [amount, setAmount] = useState("50000");
  const [reference, setReference] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  function close() {
    setIsOpen(false);
    setError(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch(`/api/agents/${agentId}/escrow/top-up`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ amountXaf: Number(amount), reference: reference || "MANUAL-CASHIER" }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? "Failed to fund escrow account");
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setIsOpen(false);
        setSucceeded(false);
        setReference("");
        router.refresh();
      }, 600);
    } catch {
      setError("Unable to reach the server");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="h-12 px-6 bg-primary text-on-primary font-semibold text-sm rounded-[var(--radius-md)] flex items-center justify-center gap-2 cursor-pointer hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
      >
        <Icon name="lock" className="size-5" />
        Fund Escrow
      </button>

      {isOpen && (
        <div className="overlay-fade-in fixed inset-0 bg-primary/60 z-40 flex items-center justify-center p-4">
          <div className="panel-scale-in bg-surface-container-lowest rounded-[var(--radius-md)] border border-outline-variant w-full max-w-lg overflow-hidden flex flex-col">
            <form onSubmit={handleSubmit} className="flex flex-col">
              <div className="p-6 border-b border-outline-variant flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center">
                    <Icon name="lock" className="size-5" />
                  </div>
                  <div>
                    <h2 className="text-h2 text-primary">Fund Escrow Account</h2>
                    <p className="text-xs text-on-surface-variant">Agent: {agentId}</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={close}
                  className="text-on-surface-variant hover:text-error cursor-pointer transition-[background-color,color,transform] duration-150 ease-out hover:scale-110 active:scale-90 p-2 rounded-full hover:bg-error-container"
                  aria-label="Close"
                >
                  <Icon name="close" className="size-5" />
                </button>
              </div>

              <div className="p-6 flex-1 space-y-6 text-left">
                <div className="bg-primary-fixed/20 border border-primary-fixed p-4 rounded-[var(--radius-sm)] flex gap-3 items-start">
                  <Icon name="check-circle" className="size-5 text-primary-fixed-dim shrink-0" />
                  <div className="text-sm text-on-primary-fixed-variant">
                    This credits the agent&apos;s wallet and permanently raises their escrow ceiling by the same amount — this is different from a temporary waiver.
                    {isPendingCeiling && " This agent is PENDING_CEILING: funding their escrow for the first time activates them automatically."}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">Top-Up Amount (XAF)</label>
                  <div className="relative">
                    <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-on-surface-variant font-semibold text-xs">XAF</span>
                    <input
                      className="w-full h-12 pl-14 pr-4 bg-surface-container-lowest border border-outline-variant rounded-[var(--radius-sm)] text-primary text-sm focus:outline-none focus:border-2 focus:border-primary"
                      type="number"
                      min={1}
                      required
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">Payment Reference (optional)</label>
                  <input
                    className="w-full h-12 px-4 bg-surface-container-lowest border border-outline-variant rounded-[var(--radius-sm)] text-primary text-sm focus:outline-none focus:border-2 focus:border-primary"
                    placeholder="e.g. cashier receipt number"
                    value={reference}
                    onChange={(e) => setReference(e.target.value)}
                  />
                </div>

                {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
              </div>

              <div className="p-6 border-t border-outline-variant flex flex-col sm:flex-row gap-4 justify-end">
                <button
                  type="button"
                  onClick={close}
                  className="h-12 px-8 bg-surface-container-lowest border-2 border-primary text-primary font-semibold text-sm rounded-[var(--radius-md)] cursor-pointer hover:bg-surface-container-low transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98] order-2 sm:order-1"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading || succeeded}
                  className={`h-12 px-8 font-semibold text-sm rounded-[var(--radius-md)] cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98] disabled:cursor-not-allowed disabled:hover:scale-100 disabled:active:scale-100 order-1 sm:order-2 disabled:opacity-80 flex items-center justify-center gap-2 ${
                    succeeded ? "bg-success-emerald text-white" : "bg-primary text-on-primary hover:bg-primary/90"
                  }`}
                >
                  {succeeded ? (
                    <>
                      <Icon name="check-circle" className="size-5" />
                      Funded
                    </>
                  ) : loading ? (
                    "Funding…"
                  ) : (
                    "Fund Escrow"
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}
