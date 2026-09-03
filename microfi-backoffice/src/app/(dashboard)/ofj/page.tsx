import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Table, Thead, Th, Tbody, Tr, Td, EmptyState } from "@/components/Table";
import { Badge } from "@/components/Badge";
import { Icon, type IconName } from "@/components/Icon";
import { AutoRefresh } from "@/components/AutoRefresh";
import type { ReactNode } from "react";
import type { AgentResponse, BranchResponse, OfjPendingLineResponse, OfjSummaryResponse, VarianceDebtResponse } from "@/lib/types";
import { OfjExportButtons, type OfjExportRow } from "./OfjExportButtons";
import { VarianceExportButtons, type VarianceExportRow } from "./VarianceExportButtons";
import { BranchSelector } from "./BranchSelector";
import { RecordVarianceModal } from "./RecordVarianceModal";
import { WriteOffVarianceDebtModal } from "./WriteOffVarianceDebtModal";
import { HistoryDateRangeFilter } from "./HistoryDateRangeFilter";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { t } from "@/lib/i18n/format";

type Tab = "summary" | "history" | "variance";

function isoDaysAgo(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

export default async function OfjOversightPage({
  searchParams,
}: {
  searchParams: Promise<{ branchId?: string; tab?: string; openOnly?: string; from?: string; to?: string }>;
}) {
  const dict = getDictionary(await getLocale());
  const TABS: { key: Tab; label: string; icon: IconName }[] = [
    { key: "summary", label: dict.ofj.tabs.summary, icon: "reports" },
    { key: "history", label: dict.ofj.tabs.history, icon: "history" },
    { key: "variance", label: dict.ofj.tabs.variance, icon: "warning" },
  ];
  const [session, branches] = await Promise.all([getSession(), api.get<BranchResponse[]>("/admin/branches")]);
  const params = await searchParams;
  const tab: Tab = params.tab === "history" || params.tab === "variance" ? params.tab : "summary";
  const openOnly = params.openOnly === "true";
  const from = params.from ?? isoDaysAgo(30);
  const to = params.to ?? isoDaysAgo(0);
  const generatedBy = session?.sub ?? "";

  const branchId = session?.role === "ADMIN" ? params.branchId ?? branches[0]?.id : session?.branchId ?? branches[0]?.id;

  if (!branchId) {
    return <EmptyState>{dict.ofj.noBranches}</EmptyState>;
  }

  const branch = branches.find((b) => b.id === branchId);
  const agents = await api.get<AgentResponse[]>("/admin/agents");
  const agentById = new Map(agents.map((a) => [a.id, a]));
  // UC-17 actor: Branch Manager / Administrator, own branch only (POST /ofj/{branch}/variance).
  const canRecordVariance = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-6">
      <AutoRefresh />
      <PageHeader title={dict.ofj.pageTitle} subtitle={dict.ofj.pageSubtitle} />

      <div className="flex items-center justify-between flex-wrap gap-4">
        {session?.role === "ADMIN" ? (
          <div className="flex items-center gap-3 flex-wrap w-full sm:w-auto">
            <span className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant shrink-0">
              <Icon name="location-on" className="size-5 text-primary" />
              {dict.ofj.viewingBranch}
            </span>
            <BranchSelector branches={branches} selectedBranchId={branchId} />
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant">
            <Icon name="location-on" className="size-5 text-primary" />
            {branch ? `${branch.name} (${branch.code})` : dict.ofj.branchFallback}
          </div>
        )}
        {/* This page is read-only oversight (see subtitle) — the actual physical-count
            reconciliation workspace lives at /cashier. Without this link, every role including
            ADMIN lands here (the page UC-16 names) with no path at all into reconciling.
            Available any time now — reconciliation is no longer gated on the branch's closing
            time (an agent can hand in cash and get reconciled the moment they're done, rather
            than everyone waiting until end of day). */}
        <Link
          href={`/cashier?branchId=${branchId}`}
          className="inline-flex items-center justify-center gap-2 min-h-12 px-4 rounded-[var(--radius-md)] text-sm font-semibold bg-primary text-on-primary hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
        >
          <Icon name="check-circle" className="size-5" />
          {dict.ofj.reconcileCash}
        </Link>
      </div>

      <div className="flex gap-1 border-b-2 border-outline-variant">
        {TABS.map((tabItem) => (
          <Link
            key={tabItem.key}
            href={`/ofj?branchId=${branchId}&tab=${tabItem.key}`}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 -mb-0.5 transition-colors ${
              tab === tabItem.key ? "border-primary text-primary" : "border-transparent text-text-slate hover:text-primary"
            }`}
          >
            <Icon name={tabItem.icon} className="size-4" />
            {tabItem.label}
          </Link>
        ))}
      </div>

      {tab === "summary" && (
        <SummaryView branchId={branchId} branchLabel={branch ? `${branch.name} (${branch.code})` : branchId} agentById={agentById} canRecordVariance={canRecordVariance} generatedBy={generatedBy} />
      )}
      {tab === "history" && (
        <HistoryView branchId={branchId} branchLabel={branch ? `${branch.name} (${branch.code})` : branchId} agentById={agentById} canRecordVariance={canRecordVariance} from={from} to={to} generatedBy={generatedBy} />
      )}
      {tab === "variance" && (
        <VarianceView
          branchId={branchId}
          branchLabel={branch ? `${branch.name} (${branch.code})` : branchId}
          agentById={agentById}
          openOnly={openOnly}
          isAdmin={session?.role === "ADMIN"}
          from={from}
          to={to}
          generatedBy={generatedBy}
        />
      )}
    </div>
  );
}

function agentLabel(agentById: Map<string, AgentResponse>, agentId: string) {
  const agent = agentById.get(agentId);
  return agent ? `${agent.fullName} (${agent.employeeCode})` : agentId;
}

function SectionCard({ icon, title, right, children }: { icon: IconName; title: ReactNode; right?: ReactNode; children: ReactNode }) {
  return (
    <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
      <div className="flex items-center justify-between px-5 py-4 border-b-2 border-outline-variant bg-surface-bright">
        <div className="flex items-center gap-2 font-bold text-on-surface">
          <Icon name={icon} className="size-5 text-primary" />
          {title}
        </div>
        {right}
      </div>
      <div className="p-5 flex flex-col gap-4">{children}</div>
    </div>
  );
}

function StatCard({ icon, label, value, alert = false }: { icon: IconName; label: string; value: string; alert?: boolean }) {
  return (
    <div className={`bg-surface-container-lowest border-2 rounded-[var(--radius-md)] p-4 flex flex-col gap-3 ${alert ? "border-error/40" : "border-outline-variant"}`}>
      <div className={`h-9 w-9 rounded-[var(--radius-sm)] flex items-center justify-center ${alert ? "bg-error-container text-on-error-container" : "bg-primary-container/10 text-primary"}`}>
        <Icon name={icon} className="size-5" />
      </div>
      <div>
        <p className="text-xs text-on-surface-variant uppercase tracking-widest mb-1 font-semibold">{label}</p>
        <p className={`font-bold text-2xl tabular-nums ${alert ? "text-error" : "text-primary"}`}>{value}</p>
      </div>
    </div>
  );
}

async function SummaryView({
  branchId,
  branchLabel,
  agentById,
  canRecordVariance,
  generatedBy,
}: {
  branchId: string;
  branchLabel: string;
  agentById: Map<string, AgentResponse>;
  canRecordVariance: boolean;
  generatedBy: string;
}) {
  // Reconciled lines only exist once a cashier has physically counted an agent's cash — before
  // that, an agent who's actively collecting today was invisible on this page even though the
  // page is literally named for keeping an eye on today's collection activity. /pending is the
  // same "not yet reconciled" total /cashier's queue already uses, surfaced here too so this page
  // (the one named for it) actually shows the live picture, not just history after the fact.
  // Nothing about reconciliation itself changes — POST /ofj/{branchId}/reconcile still enforces
  // the closing-time gate exactly as before; this only affects what's visible, never when the
  // physical count can happen.
  const [summary, pending] = await Promise.all([
    api.get<OfjSummaryResponse>(`/ofj/${branchId}/summary`),
    api.get<OfjPendingLineResponse[]>(`/ofj/${branchId}/pending`),
  ]);
  const totalDigital = summary.agentLines.reduce((sum, l) => sum + l.digitalTotalXaf, 0) + pending.reduce((sum, p) => sum + p.digitalTotalXaf, 0);
  const totalPhysical = summary.agentLines.reduce((sum, l) => sum + l.physicalTotalXaf, 0);
  const netVariance = summary.agentLines.reduce((sum, l) => sum + l.deltaXaf, 0);
  const agentsReporting = summary.agentLines.length + pending.length;

  const dict = getDictionary(await getLocale());

  const exportRows: OfjExportRow[] = [
    ...pending.map((p) => ({
      agentLabel: agentLabel(agentById, p.agentId),
      digitalTotalXaf: p.digitalTotalXaf,
      physicalTotalXaf: 0,
      deltaXaf: null,
      status: dict.common.status.PENDING,
    })),
    ...summary.agentLines.map((line) => ({
      agentLabel: agentLabel(agentById, line.agentId),
      digitalTotalXaf: line.digitalTotalXaf,
      physicalTotalXaf: line.physicalTotalXaf,
      deltaXaf: line.deltaXaf,
      status: line.resolved ? dict.common.status.RESOLVED : dict.common.status.OPEN,
    })),
  ];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-end">
        <OfjExportButtons
          filenameBase={`microfi-ofj-summary_${branchId}_${summary.businessDate}`}
          sheetName={dict.ofj.tabs.summary}
          pdfTitle={dict.ofj.tabs.summary}
          meta={{ scope: branchLabel, from: summary.businessDate, to: summary.businessDate, generatedBy }}
          rows={exportRows}
        />
      </div>
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon="agents" label={dict.ofj.summary.agentsReporting} value={agentsReporting.toLocaleString()} />
        <StatCard icon="account-balance-wallet" label={dict.ofj.summary.totalDigital} value={`${totalDigital.toLocaleString()} XAF`} />
        <StatCard icon="lock" label={dict.ofj.summary.totalPhysical} value={`${totalPhysical.toLocaleString()} XAF`} />
        <StatCard icon="warning" label={dict.ofj.summary.netVariance} value={`${netVariance.toLocaleString()} XAF`} alert={netVariance < 0} />
      </div>

      <SectionCard icon="reports" title={t(dict.ofj.businessDate, { date: summary.businessDate })} right={<Badge status={summary.status} />}>
        <Table>
          <Thead>
            <Th>{dict.dashboard.colAgent}</Th>
            <Th>{dict.dashboard.colDigitalTotal}</Th>
            <Th>{dict.dashboard.colPhysicalTotal}</Th>
            <Th>{dict.dashboard.colDelta}</Th>
            <Th>{dict.dashboard.colStatus}</Th>
            {canRecordVariance && <Th>{dict.common.actions}</Th>}
          </Thead>
          <Tbody>
            {pending.map((p) => (
              <Tr key={`pending-${p.agentId}`}>
                <Td className="font-medium text-on-surface">{agentLabel(agentById, p.agentId)}</Td>
                <Td>{p.digitalTotalXaf.toLocaleString()} XAF</Td>
                <Td className="text-on-surface-variant">N/A</Td>
                <Td className="text-on-surface-variant">N/A</Td>
                <Td><Badge status="PENDING" /></Td>
                {canRecordVariance && <Td>{null}</Td>}
              </Tr>
            ))}
            {summary.agentLines.map((line) => {
              const isShortage = !line.resolved && line.deltaXaf < 0;
              return (
                <Tr key={line.id} tint={isShortage}>
                  <Td className="font-medium text-on-surface">{agentLabel(agentById, line.agentId)}</Td>
                  <Td>{line.digitalTotalXaf.toLocaleString()} XAF</Td>
                  <Td>{line.physicalTotalXaf.toLocaleString()} XAF</Td>
                  <Td className={line.deltaXaf < 0 ? "text-danger-red font-semibold" : "text-secondary font-semibold"}>
                    {line.deltaXaf.toLocaleString()} XAF
                  </Td>
                  <Td>
                    <div className="flex flex-col gap-1 items-start">
                      {line.resolved ? <Badge status="RESOLVED" /> : <Badge status="OPEN" />}
                      {line.pendingConfirmationCount > 0 && (
                        <span className="text-xs text-tertiary-fixed-dim font-semibold">{t(dict.ofj.awaitingAgentConfirmation, { count: line.pendingConfirmationCount })}</span>
                      )}
                    </div>
                  </Td>
                  {canRecordVariance && (
                    <Td>
                      {isShortage && (
                        <RecordVarianceModal
                          branchId={branchId}
                          ofjAgentLineId={line.id}
                          agentLabel={agentLabel(agentById, line.agentId)}
                          shortageXaf={Math.abs(line.deltaXaf)}
                        />
                      )}
                    </Td>
                  )}
                </Tr>
              );
            })}
          </Tbody>
        </Table>
        {agentsReporting === 0 && <EmptyState>{dict.ofj.summary.noActivity}</EmptyState>}
      </SectionCard>
    </div>
  );
}

async function HistoryView({
  branchId,
  branchLabel,
  agentById,
  canRecordVariance,
  from,
  to,
  generatedBy,
}: {
  branchId: string;
  branchLabel: string;
  agentById: Map<string, AgentResponse>;
  canRecordVariance: boolean;
  from: string;
  to: string;
  generatedBy: string;
}) {
  const dict = getDictionary(await getLocale());
  const history = await api.get<OfjSummaryResponse[]>(`/ofj/${branchId}/history?from=${from}&to=${to}`);

  const exportRows: OfjExportRow[] = history.flatMap((pastSession) =>
    pastSession.agentLines.map((line) => ({
      businessDate: pastSession.businessDate,
      agentLabel: agentLabel(agentById, line.agentId),
      digitalTotalXaf: line.digitalTotalXaf,
      physicalTotalXaf: line.physicalTotalXaf,
      deltaXaf: line.deltaXaf,
      status: line.resolved ? dict.common.status.RESOLVED : dict.common.status.OPEN,
    }))
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <HistoryDateRangeFilter branchId={branchId} tab="history" from={from} to={to} />
        <OfjExportButtons
          filenameBase={`microfi-ofj-history_${branchId}_${from}_${to}`}
          sheetName={dict.ofj.tabs.history}
          pdfTitle={dict.ofj.tabs.history}
          meta={{ scope: branchLabel, from, to, generatedBy }}
          rows={exportRows}
          includeBusinessDate
        />
      </div>
      {history.map((pastSession) => (
        <SectionCard key={pastSession.sessionId} icon="reports" title={pastSession.businessDate} right={<Badge status={pastSession.status} />}>
          <Table>
            <Thead>
              <Th>{dict.dashboard.colAgent}</Th>
              <Th>{dict.dashboard.colDigitalTotal}</Th>
              <Th>{dict.dashboard.colPhysicalTotal}</Th>
              <Th>{dict.dashboard.colDelta}</Th>
              <Th>{dict.ofj.history.colResolved}</Th>
              {canRecordVariance && <Th>{dict.common.actions}</Th>}
            </Thead>
            <Tbody>
              {pastSession.agentLines.map((line) => {
                const isShortage = !line.resolved && line.deltaXaf < 0;
                return (
                  <Tr key={line.id} tint={isShortage}>
                    <Td className="font-medium text-on-surface">{agentLabel(agentById, line.agentId)}</Td>
                    <Td>{line.digitalTotalXaf.toLocaleString()} XAF</Td>
                    <Td>{line.physicalTotalXaf.toLocaleString()} XAF</Td>
                    <Td className={line.deltaXaf < 0 ? "text-danger-red font-semibold" : "text-secondary font-semibold"}>
                      {line.deltaXaf.toLocaleString()} XAF
                    </Td>
                    <Td>
                    <div className="flex flex-col gap-1 items-start">
                      {line.resolved ? <Badge status="RESOLVED" /> : <Badge status="OPEN" />}
                      {line.pendingConfirmationCount > 0 && (
                        <span className="text-xs text-tertiary-fixed-dim font-semibold">{t(dict.ofj.awaitingAgentConfirmation, { count: line.pendingConfirmationCount })}</span>
                      )}
                    </div>
                  </Td>
                    {canRecordVariance && (
                      <Td>
                        {isShortage && (
                          <RecordVarianceModal
                            branchId={branchId}
                            ofjAgentLineId={line.id}
                            agentLabel={agentLabel(agentById, line.agentId)}
                            shortageXaf={Math.abs(line.deltaXaf)}
                          />
                        )}
                      </Td>
                    )}
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </SectionCard>
      ))}
      {history.length === 0 && <EmptyState>{dict.ofj.history.noSessions}</EmptyState>}
    </div>
  );
}

async function VarianceView({
  branchId,
  branchLabel,
  agentById,
  openOnly,
  isAdmin,
  from,
  to,
  generatedBy,
}: {
  branchId: string;
  branchLabel: string;
  agentById: Map<string, AgentResponse>;
  openOnly: boolean;
  isAdmin: boolean;
  from: string;
  to: string;
  generatedBy: string;
}) {
  const dict = getDictionary(await getLocale());
  const debts = await api.get<VarianceDebtResponse[]>(`/ofj/${branchId}/variance-debts?openOnly=${openOnly}`);
  const openCount = debts.filter((d) => d.status === "OPEN").length;
  const totalAmount = debts.reduce((sum, d) => sum + d.amountXaf, 0);

  // Export honors the chosen period regardless of the open/all toggle currently browsed — the
  // visible list stays toggle-filtered, only the export additionally bounds by createdAt.
  const fromInstant = new Date(`${from}T00:00:00Z`).getTime();
  const toInstant = new Date(`${to}T23:59:59Z`).getTime();
  const exportRows: VarianceExportRow[] = debts
    .filter((d) => {
      const created = new Date(d.createdAt).getTime();
      return created >= fromInstant && created <= toInstant;
    })
    .map((d) => ({
      agentLabel: agentLabel(agentById, d.agentId),
      amountXaf: d.amountXaf,
      status: dict.common.status[d.status],
      recordedAt: new Date(d.createdAt).toLocaleString(),
      writtenOffReason: d.writtenOffReason ?? "",
    }));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <HistoryDateRangeFilter branchId={branchId} tab="variance" from={from} to={to} extraQuery={`&openOnly=${openOnly}`} />
        <VarianceExportButtons
          filenameBase={`microfi-variance-debts_${branchId}_${from}_${to}`}
          meta={{ scope: branchLabel, from, to, generatedBy }}
          rows={exportRows}
        />
      </div>
      <div className="grid grid-cols-2 sm:max-w-md gap-4">
        <StatCard icon="warning" label={dict.ofj.variance.openDebts} value={openCount.toLocaleString()} alert={openCount > 0} />
        <StatCard icon="account-balance-wallet" label={dict.ofj.variance.totalAmount} value={`${totalAmount.toLocaleString()} XAF`} />
      </div>

      <SectionCard
        icon="warning"
        title={dict.ofj.variance.title}
        right={
          <Link href={`/ofj?branchId=${branchId}&tab=variance&openOnly=${!openOnly}&from=${from}&to=${to}`} className="text-sm text-primary hover:underline underline-offset-2 font-medium">
            {openOnly ? dict.ofj.variance.showAllDebts : dict.ofj.variance.showOpenOnly}
          </Link>
        }
      >
        <Table>
          <Thead>
            <Th>{dict.dashboard.colAgent}</Th>
            <Th>{dict.ofj.variance.colAmount}</Th>
            <Th>{dict.dashboard.colStatus}</Th>
            <Th>{dict.ofj.variance.colRecorded}</Th>
            {isAdmin && <Th>{dict.ofj.variance.colActions}</Th>}
          </Thead>
          <Tbody>
            {debts.map((debt) => (
              <Tr key={debt.id}>
                <Td className="font-medium text-on-surface">{agentLabel(agentById, debt.agentId)}</Td>
                <Td>{debt.amountXaf.toLocaleString()} XAF</Td>
                <Td>
                  <Badge status={debt.status} />
                  {debt.status === "WRITTEN_OFF" && debt.writtenOffReason && (
                    <p className="text-xs text-on-surface-variant mt-1 max-w-[240px]">
                      {t(dict.ofj.variance.writtenOffNote, { reason: debt.writtenOffReason })}
                    </p>
                  )}
                </Td>
                <Td>{new Date(debt.createdAt).toLocaleString()}</Td>
                {isAdmin && (
                  <Td>
                    {debt.status === "OPEN" && (
                      <WriteOffVarianceDebtModal debtId={debt.id} agentLabel={agentLabel(agentById, debt.agentId)} amountXaf={debt.amountXaf} />
                    )}
                  </Td>
                )}
              </Tr>
            ))}
          </Tbody>
        </Table>
        {debts.length === 0 && <EmptyState>{dict.ofj.variance.noDebts}</EmptyState>}
      </SectionCard>
    </div>
  );
}
