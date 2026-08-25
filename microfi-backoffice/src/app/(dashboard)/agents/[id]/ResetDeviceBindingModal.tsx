"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/Icon";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

export function ResetDeviceBindingModal({ agentId, bound }: { agentId: string; bound: boolean }) {
  const router = useRouter();
  const dict = useDictionary();
  const [isOpen, setIsOpen] = useState(false);
  const [reason, setReason] = useState("");
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
      const res = await fetch(`/api/agents/${agentId}/device-binding`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.agents.resetDeviceBinding.failedToReset);
        return;
      }
      setSucceeded(true);
      setTimeout(() => {
        setIsOpen(false);
        setSucceeded(false);
        setReason("");
        router.refresh();
      }, 600);
    } catch {
      setError(dict.common.unableToReachServer);
    } finally {
      setLoading(false);
    }
  }

  if (!bound) {
    return null;
  }

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="h-12 px-6 bg-surface-container-lowest border-2 border-error text-error font-semibold text-sm rounded-[var(--radius-md)] flex items-center justify-center gap-2 cursor-pointer hover:bg-error-container transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
      >
        <Icon name="phone" className="size-5" />
        {dict.agents.resetDeviceBinding.button}
      </button>

      {isOpen && (
        <div className="overlay-fade-in fixed inset-0 bg-primary/60 z-40 flex items-center justify-center p-4">
          <div className="panel-scale-in bg-surface-container-lowest rounded-[var(--radius-md)] border border-outline-variant w-full max-w-lg overflow-hidden flex flex-col">
            <form onSubmit={handleSubmit} className="flex flex-col">
              <div className="p-6 border-b border-outline-variant flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-error-container text-on-error-container flex items-center justify-center">
                    <Icon name="phone" className="size-5" />
                  </div>
                  <div>
                    <h2 className="text-h2 text-primary">{dict.agents.resetDeviceBinding.title}</h2>
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

              <div className="p-6 flex-1 space-y-6 text-left">
                <div className="bg-error-container/40 border border-error p-4 rounded-[var(--radius-sm)] flex gap-3 items-start">
                  <Icon name="warning" filled className="size-5 text-error shrink-0" />
                  <div className="text-sm text-on-error-container">
                    {dict.agents.resetDeviceBinding.warning}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">
                    {dict.agents.resetDeviceBinding.reasonLabel} <span className="text-error">*</span>
                  </label>
                  <textarea
                    className="w-full p-4 bg-surface-container-lowest border border-outline-variant rounded-[var(--radius-sm)] text-primary text-sm focus:outline-none focus:border-2 focus:border-primary resize-none min-h-[100px]"
                    placeholder={dict.agents.resetDeviceBinding.reasonPlaceholder}
                    required
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
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
                  {dict.common.cancel}
                </button>
                <button
                  type="submit"
                  disabled={loading || succeeded}
                  className={`h-12 px-8 font-semibold text-sm rounded-[var(--radius-md)] cursor-pointer transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98] disabled:cursor-not-allowed disabled:hover:scale-100 disabled:active:scale-100 order-1 sm:order-2 disabled:opacity-80 flex items-center justify-center gap-2 ${
                    succeeded ? "bg-success-emerald text-white" : "bg-error text-on-error hover:bg-error/90"
                  }`}
                >
                  {succeeded ? (
                    <>
                      <Icon name="check-circle" className="size-5" />
                      {dict.agents.resetDeviceBinding.resetDone}
                    </>
                  ) : loading ? (
                    dict.agents.resetDeviceBinding.resetting
                  ) : (
                    dict.agents.resetDeviceBinding.button
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
