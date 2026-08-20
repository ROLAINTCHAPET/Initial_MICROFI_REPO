import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { EmptyState } from "@/components/Table";
import { Icon } from "@/components/Icon";
import type { AgentResponse, BranchResponse, OfjPendingLineResponse, OfjSummaryResponse } from "@/lib/types";
import { CashierBranchSelector } from "./CashierBranchSelector";
import { ReconcileWorkspace, type QueueLine, type ValidatedLine } from "./ReconcileWorkspace";

export default async function CashierPortalPage({
  searchParams,
}: {
  searchParams: Promise<{ branchId?: string }>;
}) {
  const [session, branches] = await Promise.all([getSession(), api.get<BranchResponse[]>("/admin/branches")]);
  const params = await searchParams;
  const branchId = session?.role === "ADMIN" ? params.branchId ?? branches[0]?.id : session?.branchId ?? branches[0]?.id;

  if (!branchId) {
    return <EmptyState>No branches exist yet — create one first.</EmptyState>;
  }

  const branch = branches.find((b) => b.id === branchId);
  const [summary, pending, agents] = await Promise.all([
    api.get<OfjSummaryResponse>(`/ofj/${branchId}/summary`),
    api.get<OfjPendingLineResponse[]>(`/ofj/${branchId}/pending`),
    api.get<AgentResponse[]>("/admin/agents"),
  ]);
  const agentById = new Map(agents.map((a) => [a.id, a]));
  const label = (agentId: string) => {
    const agent = agentById.get(agentId);
    return agent ? `${agent.fullName} (${agent.employeeCode})` : agentId;
  };

  // Two distinct reasons an agent shows up as "awaiting": an existing shortage line pending a
  // variance debt, or — the more common case — cash collected today that's never been reconciled
  // at all, which summary.agentLines alone can't show (see OfjService#listPendingAgents).
  const queue: QueueLine[] = [
    ...summary.agentLines
      .filter((l) => !l.resolved)
      .map((l) => ({ lineId: l.id, agentId: l.agentId, agentLabel: label(l.agentId), digitalTotalXaf: l.digitalTotalXaf })),
    ...pending.map((p) => ({ lineId: `pending-${p.agentId}`, agentId: p.agentId, agentLabel: label(p.agentId), digitalTotalXaf: p.digitalTotalXaf })),
  ];

  const validated: ValidatedLine[] = summary.agentLines
    .filter((l) => l.resolved)
    .map((l) => ({ lineId: l.id, agentLabel: label(l.agentId), physicalTotalXaf: l.physicalTotalXaf, deltaXaf: l.deltaXaf }));

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Cashier Portal" subtitle="Physical cash count & end-of-day reconciliation." />

      <div className="flex items-center justify-between flex-wrap gap-4 max-w-7xl mx-auto w-full">
        {session?.role === "ADMIN" ? (
          <div className="flex items-center gap-3">
            <span className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant">
              <Icon name="location-on" className="size-5 text-primary" />
              Viewing branch
            </span>
            <CashierBranchSelector branches={branches} selectedBranchId={branchId} />
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant">
            <Icon name="location-on" className="size-5 text-primary" />
            {branch ? `${branch.name} (${branch.code})` : "Branch"}
          </div>
        )}
      </div>
      <ReconcileWorkspace branchId={branchId} queue={queue} validated={validated} />
    </div>
  );
}
