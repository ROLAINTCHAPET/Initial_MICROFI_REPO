import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import type { AdminUserResponse, AgentResponse, BranchResponse, EscrowResponse } from "@/lib/types";
import { RegisterUserModal } from "./RegisterUserModal";
import { AgentsExplorer, type AgentRow } from "./AgentsExplorer";

export default async function AgentsPage() {
  const [session, allAgents, branches, users] = await Promise.all([
    getSession(),
    api.get<AgentResponse[]>("/admin/agents"),
    api.get<BranchResponse[]>("/admin/branches"),
    api.get<AdminUserResponse[]>("/admin/users"),
  ]);

  // GET /admin/agents intentionally returns every agent network-wide (any Back-Office role can
  // look up an agent regardless of branch, e.g. for cross-branch cash collection) — unlike Team
  // or OFJ, it isn't scoped server-side, so the branch-scoped view for managers/cashiers is
  // applied here instead.
  const agents = session?.role === "ADMIN" ? allAgents : allAgents.filter((a) => a.branchId === session?.branchId);

  const branchById = new Map(branches.map((b) => [b.id, b]));
  const escrows = await Promise.all(agents.map((a) => api.get<EscrowResponse>(`/agents/${a.id}/escrow`).catch(() => null)));

  const rows: AgentRow[] = agents.map((agent, i) => {
    const escrow = escrows[i];
    const pct = escrow && escrow.effectiveCeilingXaf > 0 ? Math.round((escrow.cumulativeTodayXaf / escrow.effectiveCeilingXaf) * 100) : null;
    return {
      id: agent.id,
      fullName: agent.fullName,
      employeeCode: agent.employeeCode,
      branchName: branchById.get(agent.branchId)?.name ?? "—",
      status: agent.status,
      collectedTodayXaf: escrow?.cumulativeTodayXaf ?? null,
      ceilingXaf: escrow?.effectiveCeilingXaf ?? null,
      pct,
      nearLimit: pct !== null && pct >= 80,
    };
  });

  const canCreate = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";

  return (
    <div className="max-w-7xl mx-auto w-full flex flex-col gap-6">
      <PageHeader
        title="Agents Overview"
        subtitle={session?.role === "ADMIN" ? "Manage field agents and monitor escrow levels." : "Manage your branch's field agents and monitor escrow levels."}
      />
      <AgentsExplorer
        rows={rows}
        actions={
          canCreate && session ? (
            <RegisterUserModal branches={branches} users={users} callerRole={session.role} callerBranchId={session.branchId} />
          ) : undefined
        }
      />
    </div>
  );
}
