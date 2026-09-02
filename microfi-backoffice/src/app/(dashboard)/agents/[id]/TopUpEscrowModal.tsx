"use client";

import { useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import { ActionCard } from "@/components/ActionCard";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

// Red asterisk — the visual marker for every mandatory field on this form (Top-Up Amount, Proof
// of Deposit). Payment Reference is the only optional one and is left unmarked.
function Req() {
  return (
    <span className="text-danger-red ml-0.5" aria-hidden>
      *
    </span>
  );
}

export function TopUpEscrowModal({ agentId, isPendingCeiling }: { agentId: string; isPendingCeiling: boolean }) {
  const router = useRouter();
  const dict = useDictionary();
  const [isOpen, setIsOpen] = useState(false);
  const [amount, setAmount] = useState("50000");
  const [reference, setReference] = useState("");
  const [proof, setProof] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [succeeded, setSucceeded] = useState(false);

  function close() {
    setIsOpen(false);
    setError(null);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!proof) {
      setError(dict.agents.topUpEscrow.uploadProofRequired);
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const formData = new FormData();
      formData.append(
        "metadata",
        new Blob([JSON.stringify({ amountXaf: Number(amount), reference: reference || "MANUAL-CASHIER" })], { type: "application/json" })
      );
      formData.append("proof", proof);
      const res = await fetch(`/api/agents/${agentId}/escrow/top-up`, {
        method: "POST",
        body: formData,
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.agents.topUpEscrow.failedToFund);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setIsOpen(false);
        setSucceeded(false);
        setReference("");
        setProof(null);
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
      <ActionCard
        icon="account-balance-wallet"
        title={dict.agents.topUpEscrow.fundEscrow}
        description={dict.agents.topUpEscrow.cardDescription}
        onClick={() => setIsOpen(true)}
      />

      {isOpen && createPortal(
        <div className="overlay-fade-in fixed inset-0 bg-primary/60 z-40 flex items-center justify-center p-4">
          <div className="panel-scale-in bg-surface-container-lowest rounded-[var(--radius-md)] border border-outline-variant w-full max-w-lg max-h-[90vh] overflow-hidden flex flex-col">
            <form onSubmit={handleSubmit} className="flex flex-col min-h-0">
              <div className="p-6 border-b border-outline-variant flex justify-between items-center shrink-0">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center">
                    <Icon name="account-balance-wallet" className="size-5" />
                  </div>
                  <div>
                    <h2 className="text-h2 text-primary">{dict.agents.topUpEscrow.fundEscrowAccount}</h2>
                    <p className="text-xs text-on-surface-variant">{t(dict.agents.agentLabel, { id: agentId })}</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={close}
                  className="text-on-surface-variant hover:text-error cursor-pointer transition-[background-color,color,transform] duration-150 ease-out hover:scale-110 active:scale-90 p-2 rounded-full hover:bg-error-container"
                  aria-label={dict.common.close}
                >
                  <Icon name="close" className="size-5" />
                </button>
              </div>

              <div className="p-6 flex-1 min-h-0 overflow-y-auto space-y-6 text-left">
                <div className="bg-primary-fixed/20 border border-primary-fixed p-4 rounded-[var(--radius-sm)] flex gap-3 items-start">
                  <Icon name="check-circle" className="size-5 text-primary-fixed-dim shrink-0" />
                  <div className="text-sm text-on-primary-fixed-variant">
                    {dict.agents.topUpEscrow.infoCredit}
                    {isPendingCeiling && dict.agents.topUpEscrow.infoPendingCeiling}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">
                    {dict.agents.topUpEscrow.topUpAmount}
                    <Req />
                  </label>
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
                  <label className="block text-sm font-semibold text-primary mb-2">{dict.agents.topUpEscrow.paymentReference}</label>
                  <input
                    className="w-full h-12 px-4 bg-surface-container-lowest border border-outline-variant rounded-[var(--radius-sm)] text-primary text-sm focus:outline-none focus:border-2 focus:border-primary"
                    placeholder={dict.agents.topUpEscrow.paymentReferencePlaceholder}
                    value={reference}
                    onChange={(e) => setReference(e.target.value)}
                  />
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">
                    {dict.agents.topUpEscrow.proofOfDeposit}
                    <Req />
                  </label>
                  <input
                    type="file"
                    aria-label={dict.agents.topUpEscrow.proofAriaLabel}
                    accept="application/pdf,image/jpeg"
                    required
                    onChange={(e) => setProof(e.target.files?.[0] ?? null)}
                    className="w-full text-sm text-on-surface-variant file:mr-3 file:h-10 file:px-3 file:rounded-[var(--radius-sm)] file:border file:border-outline-variant file:bg-surface-container-lowest file:text-sm file:font-semibold file:cursor-pointer file:text-primary"
                  />
                  <p className="text-xs text-on-surface-variant mt-1">{dict.agents.topUpEscrow.proofHelp}</p>
                </div>

                {error && <p role="alert" className="text-sm text-danger-red">{error}</p>}
              </div>

              <div className="p-6 border-t border-outline-variant flex flex-col sm:flex-row gap-4 justify-end shrink-0">
                <button
                  type="button"
                  onClick={close}
                  className="h-12 px-8 bg-surface-container-lowest border-2 border-primary text-primary font-semibold text-sm rounded-[var(--radius-md)] cursor-pointer hover:bg-surface-container-low transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98] order-2 sm:order-1"
                >
                  {dict.common.cancel}
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
                      {dict.agents.topUpEscrow.funded}
                    </>
                  ) : loading ? (
                    dict.agents.topUpEscrow.funding
                  ) : (
                    dict.agents.topUpEscrow.fundEscrow
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>,
        document.body
      )}
    </>
  );
}
