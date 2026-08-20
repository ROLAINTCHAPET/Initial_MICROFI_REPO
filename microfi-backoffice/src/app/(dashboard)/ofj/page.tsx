import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Table, Thead, Th, Tbody, Tr, Td, EmptyState } from "@/components/Table";
import { Badge } from "@/components/Badge";
import { Icon, type IconName } from "@/components/Icon";
import type { ReactNode } from "react";
import type { AgentResponse, BranchResponse, OfjSummaryResponse, VarianceDebtResponse } from "@/lib/types";
import { BranchSelector } from "./BranchSelector";
import { RecordVarianceModal } from "./RecordVarianceModal";

type Tab = "summary" | "history" | "variance";

const TABS: { key: Tab; label: string; icon: IconName }[] = [
  { key: "summary", label: "Today's Summary", icon: "reports" },
  { key: "history", label: "History", icon: "history" },
  { key: "variance", label: "Variance Debts", icon: "warning" },
];

export default async function OfjOversightPage({
  searchParams,
}: {
  searchParams: Promise<{ branchId?: string; tab?: string; openOnly?: string }>;
}) {
  const [session, branches] = await Promise.all([getSession(), api.get<BranchResponse[]>("/admin/branches")]);
  const params = await searchParams;
  const tab: Tab = params.tab === "history" || params.tab === "variance" ? params.tab : "summary";
  const openOnly = params.openOnly === "true";

  const branchId = session?.role === "ADMIN" ? params.branchId ?? branches[0]?.id : session?.branchId ?? branches[0]?.id;

  if (!branchId) {
    return <EmptyState>No branches exist yet — create one first.</EmptyState>;
  }

  const branch = branches.find((b) => b.id === branchId);
  const agents = await api.get<AgentResponse[]>("/admin/agents");
  const agentById = new Map(agents.map((a) => [a.id, a]));
  // UC-17 actor: Branch Manager / Administrator, own branch only (POST /ofj/{branch}/variance).
  const canRecordVariance = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title="End of Day Oversight" subtitle="Digital cash-desk reconciliation, read-only from the Back-Office." />

      <div className="flex items-center justify-between flex-wrap gap-4">
        {session?.role === "ADMIN" ? (
          <div className="flex items-center gap-3">
            <span className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant">
              <Icon name="location-on" className="size-5 text-primary" />
              Viewing branch
            </span>
            <BranchSelector branches={branches} selectedBranchId={branchId} />
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant">
            <Icon name="location-on" className="size-5 text-primary" />
            {branch ? `${branch.name} (${branch.code})` : "Branch"}
          </div>
        )}
      </div>

      <div className="flex gap-1 border-b-2 border-outline-variant">
        {TABS.map((t) => (
          <Link
            key={t.key}
            href={`/ofj?branchId=${branchId}&tab=${t.key}`}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 -mb-0.5 transition-colors ${
              tab === t.key ? "border-primary text-primary" : "border-transparent text-text-slate hover:text-primary"
            }`}
          >
            <Icon name={t.icon} className="size-4" />
            {t.label}
          </Link>
        ))}
      </div>

      {tab === "summary" && <SummaryView branchId={branchId} agentById={agentById} canRecordVariance={canRecordVariance} />}
      {tab === "history" && <HistoryView branchId={branchId} agentById={agentById} canRecordVariance={canRecordVariance} />}
      {tab === "variance" && <VarianceView branchId={branchId} agentById={agentById} openOnly={openOnly} />}
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
  agentById,
  canRecordVariance,
}: {
  branchId: string;
  agentById: Map<string, AgentResponse>;
  canRecordVariance: boolean;
}) {
  const summary = await api.get<OfjSummaryResponse>(`/ofj/${branchId}/summary`);
  const totalDigital = summary.agentLines.reduce((sum, l) => sum + l.digitalTotalXaf, 0);
  const totalPhysical = summary.agentLines.reduce((sum, l) => sum + l.physicalTotalXaf, 0);
  const netVariance = summary.agentLines.reduce((sum, l) => sum + l.deltaXaf, 0);

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon="agents" label="Agents Reporting" value={summary.agentLines.length.toLocaleString()} />
        <StatCard icon="account-balance-wallet" label="Total Digital" value={`${totalDigital.toLocaleString()} XAF`} />
        <StatCard icon="lock" label="Total Physical" value={`${totalPhysical.toLocaleString()} XAF`} />
        <StatCard icon="warning" label="Net Variance" value={`${netVariance.toLocaleString()} XAF`} alert={netVariance < 0} />
      </div>

      <SectionCard icon="reports" title={`Business date: ${summary.businessDate}`} right={<Badge status={summary.status} />}>
        <Table>
          <Thead>
            <Th>Agent</Th>
            <Th>Digital Total</Th>
            <Th>Physical Total</Th>
            <Th>Delta</Th>
            <Th>Resolved</Th>
            {canRecordVariance && <Th>Actions</Th>}
          </Thead>
          <Tbody>
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
                  <Td>{line.resolved ? <Badge status="RESOLVED" /> : <Badge status="OPEN" /> }</Td>
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
        {summary.agentLines.length === 0 && <EmptyState>No agent activity recorded for this session yet.</EmptyState>}
      </SectionCard>
    </div>
  );
}

async function HistoryView({
  branchId,
  agentById,
  canRecordVariance,
}: {
  branchId: string;
  agentById: Map<string, AgentResponse>;
  canRecordVariance: boolean;
}) {
  const history = await api.get<OfjSummaryResponse[]>(`/ofj/${branchId}/history`);
  return (
    <div className="flex flex-col gap-4">
      {history.map((pastSession) => (
        <SectionCard key={pastSession.sessionId} icon="reports" title={pastSession.businessDate} right={<Badge status={pastSession.status} />}>
          <Table>
            <Thead>
              <Th>Agent</Th>
              <Th>Digital Total</Th>
              <Th>Physical Total</Th>
              <Th>Delta</Th>
              <Th>Resolved</Th>
              {canRecordVariance && <Th>Actions</Th>}
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
                    <Td>{line.resolved ? <Badge status="RESOLVED" /> : <Badge status="OPEN" />}</Td>
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
      {history.length === 0 && <EmptyState>No past End of Day sessions for this branch yet.</EmptyState>}
    </div>
  );
}

async function VarianceView({
  branchId,
  agentById,
  openOnly,
}: {
  branchId: string;
  agentById: Map<string, AgentResponse>;
  openOnly: boolean;
}) {
  const debts = await api.get<VarianceDebtResponse[]>(`/ofj/${branchId}/variance-debts?openOnly=${openOnly}`);
  const openCount = debts.filter((d) => d.status === "OPEN").length;
  const totalAmount = debts.reduce((sum, d) => sum + d.amountXaf, 0);

  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-2 sm:max-w-md gap-4">
        <StatCard icon="warning" label="Open Debts" value={openCount.toLocaleString()} alert={openCount > 0} />
        <StatCard icon="account-balance-wallet" label="Total Amount" value={`${totalAmount.toLocaleString()} XAF`} />
      </div>

      <SectionCard
        icon="warning"
        title="Variance Debts"
        right={
          <Link href={`/ofj?branchId=${branchId}&tab=variance&openOnly=${!openOnly}`} className="text-sm text-primary hover:underline underline-offset-2 font-medium">
            {openOnly ? "Show all debts" : "Show open debts only"}
          </Link>
        }
      >
        <Table>
          <Thead>
            <Th>Agent</Th>
            <Th>Amount</Th>
            <Th>Status</Th>
            <Th>Recorded</Th>
          </Thead>
          <Tbody>
            {debts.map((debt) => (
              <Tr key={debt.id}>
                <Td className="font-medium text-on-surface">{agentLabel(agentById, debt.agentId)}</Td>
                <Td>{debt.amountXaf.toLocaleString()} XAF</Td>
                <Td>
                  <Badge status={debt.status} />
                </Td>
                <Td>{new Date(debt.createdAt).toLocaleString()}</Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
        {debts.length === 0 && <EmptyState>No variance debts recorded.</EmptyState>}
      </SectionCard>
    </div>
  );
}
