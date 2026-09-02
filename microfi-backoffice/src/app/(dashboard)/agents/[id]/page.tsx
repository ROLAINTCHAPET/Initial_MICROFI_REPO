import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Badge } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import { ceilingUtilizationPct } from "@/lib/format";
import type { AgentResponse, BranchResponse, EscrowResponse } from "@/lib/types";
import { AgentCollectionsPanel } from "./AgentCollectionsPanel";
import { AgentAdministrationPanel } from "./AgentAdministrationPanel";
import { BackLink } from "@/components/BackLink";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { t } from "@/lib/i18n/format";

type Tab = "apercu" | "administration";

export default async function AgentDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ tab?: string }>;
}) {
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
  const showAdminTab = canManage && agent.status !== "DELETED";
  const { tab: rawTab } = await searchParams;
  const tab: Tab = showAdminTab && rawTab === "administration" ? "administration" : "apercu";

  const nearLimit = !!escrow && escrow.effectiveCeilingXaf > 0 && escrow.cumulativeTodayXaf / escrow.effectiveCeilingXaf >= 0.9;
  const utilization = escrow ? ceilingUtilizationPct(escrow.cumulativeTodayXaf, escrow.effectiveCeilingXaf) : 0;

  return (
    <div className="max-w-4xl mx-auto w-full">
      <PageHeader title={dict.agents.overviewTitle} subtitle={dict.agents.overviewSubtitleAdmin} />

      <BackLink href="/agents" label={dict.agents.backToAgents} />

      <div className="flex items-center text-xs text-on-surface-variant gap-2 mb-3">
        <Link href="/agents" className="hover:text-primary transition-colors">{dict.agents.detail.breadcrumbAgents}</Link>
        <Icon name="chevron-right" className="size-4" />
        <span className="text-on-surface font-semibold">{agent.employeeCode}</span>
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant shadow-sm p-6 mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center font-bold text-xl shrink-0">
            {agent.fullName.slice(0, 2).toUpperCase()}
          </div>
          <div>
            <h1 className="text-display text-primary leading-tight">{agent.fullName}</h1>
            <div className="flex items-center gap-3 text-on-surface-variant mt-1 flex-wrap">
              <span className="flex items-center gap-1 text-sm"><Icon name="reports" className="size-4" /> {agent.employeeCode}</span>
              <span className="text-outline-variant">&middot;</span>
              <span className="flex items-center gap-1 text-sm"><Icon name="location-on" className="size-4" /> {branch?.name ?? "N/A"}</span>
            </div>
          </div>
        </div>
        <div className="px-4 py-2 bg-surface-container-low rounded-[var(--radius-sm)] border-2 border-outline-variant flex flex-col items-end">
          <span className="text-xs text-on-surface-variant">{dict.agents.detail.status}</span>
          <Badge status={agent.status} />
        </div>
      </div>

      {agent.status === "DELETED" && (
        <div className="mb-6 p-4 rounded-[var(--radius-md)] bg-error-container text-on-error-container">
          <p className="font-semibold">{dict.agents.deletedBanner.title}</p>
          {agent.deletionReason && (
            <p className="text-sm mt-1">
              {dict.agents.deletedBanner.reasonPrefix} {agent.deletionReason}
            </p>
          )}
        </div>
      )}

      {showAdminTab && (
        <div className="flex gap-1 border-b-2 border-outline-variant mb-6">
          <Link
            href={`/agents/${agent.id}?tab=apercu`}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 -mb-0.5 transition-colors ${
              tab === "apercu" ? "border-primary text-primary" : "border-transparent text-text-slate hover:text-primary"
            }`}
          >
            <Icon name="eye" className="size-4" />
            {dict.agents.detail.tabs.overview}
          </Link>
          <Link
            href={`/agents/${agent.id}?tab=administration`}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 -mb-0.5 transition-colors ${
              tab === "administration" ? "border-primary text-primary" : "border-transparent text-text-slate hover:text-primary"
            }`}
          >
            <Icon name="shield-check" className="size-4" />
            {dict.agents.detail.tabs.administration}
          </Link>
        </div>
      )}

      {tab === "administration" ? (
        <AgentAdministrationPanel agent={agent} escrow={escrow} />
      ) : (
      <>
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
              <InfoField label={dict.agents.detail.branch} value={branch?.name ?? "N/A"} />
              <InfoField label={dict.agents.detail.employeeCode} value={agent.employeeCode} mono />
              <InfoField label={dict.agents.detail.transactionPin} value={agent.pinMustChange ? dict.agents.detail.pinNotSet : dict.agents.detail.pinSet} />
              {agent.deviceResetAt && (
                <InfoField label={dict.agents.detail.lastDeviceReset} value={new Date(agent.deviceResetAt).toLocaleString()} />
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="mt-4 md:mt-6">
        <AgentCollectionsPanel agentId={agent.id} agentLabel={`${agent.fullName} (${agent.employeeCode})`} generatedBy={session?.sub ?? ""} />
      </div>
      </>
      )}
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
