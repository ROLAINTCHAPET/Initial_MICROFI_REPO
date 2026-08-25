import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Badge } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import { ceilingUtilizationPct } from "@/lib/format";
import type { AgentResponse, BranchResponse, EscrowResponse } from "@/lib/types";
import { WaiverModal } from "./WaiverModal";
import { SuspendAgentButton } from "./SuspendAgentButton";
import { ResetDeviceBindingModal } from "./ResetDeviceBindingModal";
import { TopUpEscrowModal } from "./TopUpEscrowModal";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { t } from "@/lib/i18n/format";

export default async function AgentDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const dict = getDictionary(await getLocale());
  const { id } = await params;
  const [session, agent] = await Promise.all([getSession(), api.get<AgentResponse>(`/admin/agents/${id}`)]);
  const [branch, escrow] = await Promise.all([
    api.get<BranchResponse>(`/admin/branches/${agent.branchId}/schedule`).catch(() => null),
    api.get<EscrowResponse>(`/agents/${id}/escrow`).catch(() => null),
  ]);

  // Suspend/reactivate and ceiling overrides are ADMIN or BRANCH_MANAGER (own branch) only per
  // AgentManagementController — a BRANCH_CASHIER can view an agent's escrow but not act on it.
  const canManage = session?.role === "ADMIN" || (session?.role === "BRANCH_MANAGER" && session?.branchId === agent.branchId);

  const nearLimit = !!escrow && escrow.effectiveCeilingXaf > 0 && escrow.cumulativeTodayXaf / escrow.effectiveCeilingXaf >= 0.9;
  const utilization = escrow ? ceilingUtilizationPct(escrow.cumulativeTodayXaf, escrow.effectiveCeilingXaf) : 0;

  return (
    <div className="max-w-4xl mx-auto w-full">
      <PageHeader title={dict.agents.overviewTitle} subtitle={dict.agents.overviewSubtitleAdmin} />

      <div className="mb-6">
        <div className="flex items-center text-xs text-on-surface-variant gap-2 mb-2">
          <Link href="/agents" className="hover:text-primary transition-colors">{dict.agents.detail.breadcrumbAgents}</Link>
          <Icon name="chevron-right" className="size-4" />
          <span className="text-on-surface font-semibold">{agent.employeeCode}</span>
        </div>

        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-display text-primary">{agent.fullName}</h1>
              <Badge status={agent.status} />
            </div>
            <p className="text-sm text-on-surface-variant mt-1">{agent.employeeCode} &middot; {branch?.name ?? "—"}</p>
          </div>
          {canManage && (
            <div className="flex gap-3">
              <SuspendAgentButton agentId={agent.id} status={agent.status} hasCeiling={(escrow?.baseCeilingXaf ?? 0) > 0} />
              <TopUpEscrowModal agentId={agent.id} isPendingCeiling={agent.status === "PENDING_CEILING"} />
              <WaiverModal agentId={agent.id} currentCeiling={escrow?.effectiveCeilingXaf ?? 0} />
              <ResetDeviceBindingModal agentId={agent.id} bound={agent.imei !== null} />
            </div>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-12 gap-4 md:gap-6">
        <div className="md:col-span-8 flex flex-col gap-4 md:gap-6">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-6 shadow-sm">
            <div className="flex justify-between items-center mb-4">
              <h2 className="text-h2 text-primary flex items-center gap-2">
                <Icon name="lock" className="size-5 text-primary-fixed-dim" />
                {dict.agents.detail.escrowBalance}
              </h2>
              {nearLimit && (
                <span className="bg-error-container text-on-error-container font-semibold text-xs px-3 py-1 rounded-full flex items-center gap-1 border border-error">
                  <Icon name="warning" filled className="size-4" />
                  {dict.agents.detail.nearLimit}
                </span>
              )}
            </div>

            {escrow ? (
              <div className="flex flex-col md:flex-row gap-6 items-center">
                <div className="flex-1 text-center md:text-left">
                  <div className="text-xs text-on-surface-variant uppercase tracking-wider mb-1">{dict.agents.detail.collectedTodayXaf}</div>
                  <div className="text-display text-primary">{escrow.cumulativeTodayXaf.toLocaleString()}</div>
                </div>

                <div className="flex-1 w-full max-w-sm">
                  <div className="flex justify-between text-xs text-on-surface-variant mb-2">
                    <span>0</span>
                    <span>{t(dict.agents.detail.limitLabel, { value: escrow.effectiveCeilingXaf.toLocaleString() })}</span>
                  </div>
                  <div className="h-4 bg-surface-container-high rounded-full overflow-hidden border border-outline-variant/50">
                    <div className={`${nearLimit ? "bg-error" : "bg-primary"} h-full rounded-full transition-all`} style={{ width: `${utilization}%` }} />
                  </div>
                  <div className={`text-center mt-2 text-xs font-semibold ${nearLimit ? "text-error" : "text-on-surface-variant"}`}>{t(dict.agents.detail.utilization, { pct: utilization })}</div>
                </div>
              </div>
            ) : (
              <p className="text-sm text-on-surface-variant">{dict.agents.detail.escrowUnavailable}</p>
            )}

            {escrow && (
              <div className="mt-4 pt-4 border-t border-outline-variant text-xs text-on-surface-variant flex justify-between">
                <span>{dict.agents.detail.fundedCeilingBase} <strong className="text-on-surface">{escrow.baseCeilingXaf.toLocaleString()} XAF</strong></span>
                {escrow.baseCeilingXaf === 0 && <span className="text-error font-semibold">{dict.agents.detail.neverFunded}</span>}
              </div>
            )}

            {escrow?.activeOverrideReason && (
              <div className="mt-4 pt-4 border-t border-outline-variant text-xs text-on-surface-variant">
                <span className="font-semibold text-on-surface">{dict.agents.detail.activeWaiver}</span> {escrow.activeOverrideReason}
                {escrow.overrideValidUntil && t(dict.agents.detail.validUntilSuffix, { date: new Date(escrow.overrideValidUntil).toLocaleString() })}
              </div>
            )}
          </div>
        </div>

        <div className="md:col-span-4 flex flex-col gap-4 md:gap-6">
          <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-4 shadow-sm">
            <div className="flex items-center gap-4 mb-4">
              <div className="w-14 h-14 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-xl border-2 border-outline-variant">
                {agent.fullName.charAt(0)}
              </div>
              <div>
                <div className="font-semibold text-sm text-primary">{dict.agents.detail.status}</div>
                <Badge status={agent.status} />
              </div>
            </div>

            <div className="space-y-3 pt-3 border-t border-outline-variant">
              <InfoField label={dict.agents.detail.username} value={agent.username} mono />
              <InfoField label={dict.agents.detail.phone} value={agent.phone} />
              <InfoField label={dict.agents.detail.deviceBinding} value={agent.imei !== null ? dict.agents.detail.bound : dict.agents.detail.notBound} mono />
              <InfoField label={dict.agents.detail.branch} value={branch?.name ?? "—"} />
              <InfoField label={dict.agents.detail.employeeCode} value={agent.employeeCode} mono />
              <InfoField label={dict.agents.detail.transactionPin} value={agent.pinMustChange ? dict.agents.detail.pinNotSet : dict.agents.detail.pinSet} />
              {agent.deviceResetReason && (
                <InfoField
                  label={dict.agents.detail.lastDeviceReset}
                  value={`${agent.deviceResetReason}${agent.deviceResetAt ? ` — ${new Date(agent.deviceResetAt).toLocaleString()}` : ""}`}
                />
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function InfoField({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <div className="text-xs text-on-surface-variant">{label}</div>
      <div className={`text-sm text-primary ${mono ? "font-mono" : ""}`}>{value}</div>
    </div>
  );
}
