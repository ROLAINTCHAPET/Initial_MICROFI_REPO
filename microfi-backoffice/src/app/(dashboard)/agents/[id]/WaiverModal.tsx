"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Icon } from "@/components/Icon";
import { TimePicker } from "@/components/TimePicker";
import { useDictionary } from "@/lib/i18n/I18nProvider";
import { t } from "@/lib/i18n/format";

function defaultDate() {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toISOString().slice(0, 10);
}

export function WaiverModal({ agentId, currentCeiling }: { agentId: string; currentCeiling: number }) {
  const router = useRouter();
  const dict = useDictionary();
  const [isOpen, setIsOpen] = useState(false);
  const [tempCeiling, setTempCeiling] = useState(String(Math.round(currentCeiling * 1.4) || 1_000_000));
  const [validDate, setValidDate] = useState(defaultDate());
  const [validTime, setValidTime] = useState("18:00");
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
      const validUntil = new Date(`${validDate}T${validTime}:00`).toISOString();
      const res = await fetch(`/api/agents/${agentId}/ceiling`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tempCeilingXaf: Number(tempCeiling), reason, validUntil }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => null);
        setError(body?.message ?? dict.agents.waiver.failedToApply);
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

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="h-12 px-6 bg-primary text-on-primary font-semibold text-sm rounded-[var(--radius-md)] flex items-center justify-center gap-2 cursor-pointer hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
      >
        <Icon name="pencil" className="size-5" />
        {dict.agents.waiver.temporaryWaiver}
      </button>

      {isOpen && (
        <div className="overlay-fade-in fixed inset-0 bg-primary/60 z-40 flex items-center justify-center p-4">
          <div className="panel-scale-in bg-surface-container-lowest rounded-[var(--radius-md)] border border-outline-variant w-full max-w-lg overflow-hidden flex flex-col">
            <form onSubmit={handleSubmit} className="flex flex-col">
              <div className="p-6 border-b border-outline-variant flex justify-between items-center">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center">
                    <Icon name="pencil" className="size-5" />
                  </div>
                  <div>
                    <h2 className="text-h2 text-primary">{dict.agents.waiver.temporaryCeilingWaiver}</h2>
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
                <div className="bg-primary-fixed/20 border border-primary-fixed p-4 rounded-[var(--radius-sm)] flex gap-3 items-start">
                  <Icon name="check-circle" className="size-5 text-primary-fixed-dim shrink-0" />
                  <div className="text-sm text-on-primary-fixed-variant">
                    {t(dict.agents.waiver.infoCurrent, { value: currentCeiling.toLocaleString() })}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">{dict.agents.waiver.tempCeilingAmount}</label>
                  <div className="relative">
                    <span className="absolute inset-y-0 left-0 flex items-center pl-4 text-on-surface-variant font-semibold text-xs">XAF</span>
                    <input
                      className="w-full h-12 pl-14 pr-4 bg-surface-container-lowest border border-outline-variant rounded-[var(--radius-sm)] text-primary text-sm focus:outline-none focus:border-2 focus:border-primary"
                      type="number"
                      min={1}
                      required
                      value={tempCeiling}
                      onChange={(e) => setTempCeiling(e.target.value)}
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">{dict.agents.waiver.validUntilLabel}</label>
                  <div className="grid grid-cols-2 gap-4">
                    <input
                      className="w-full h-12 px-3 bg-surface-container-lowest border border-outline-variant rounded-[var(--radius-sm)] text-primary text-sm focus:outline-none focus:border-2 focus:border-primary"
                      type="date"
                      required
                      value={validDate}
                      onChange={(e) => setValidDate(e.target.value)}
                    />
                    <TimePicker value={validTime} onChange={setValidTime} />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-semibold text-primary mb-2">
                    {dict.agents.waiver.reasonLabel} <span className="text-error">*</span>
                  </label>
                  <textarea
                    className="w-full p-4 bg-surface-container-lowest border border-outline-variant rounded-[var(--radius-sm)] text-primary text-sm focus:outline-none focus:border-2 focus:border-primary resize-none min-h-[100px]"
                    placeholder={dict.agents.waiver.reasonPlaceholder}
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
                    succeeded ? "bg-success-emerald text-white" : "bg-primary text-on-primary hover:bg-primary/90"
                  }`}
                >
                  {succeeded ? (
                    <>
                      <Icon name="check-circle" className="size-5" />
                      {dict.agents.waiver.waiverApplied}
                    </>
                  ) : loading ? (
                    dict.agents.waiver.applying
                  ) : (
                    dict.agents.waiver.approveWaiver
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
