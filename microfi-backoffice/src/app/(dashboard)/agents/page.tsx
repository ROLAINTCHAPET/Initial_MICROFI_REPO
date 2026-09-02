import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Icon } from "@/components/Icon";
import { ceilingUtilizationPct } from "@/lib/format";
import type { AgentResponse, BranchResponse, EscrowResponse } from "@/lib/types";
import { AgentsExplorer, type AgentRow } from "./AgentsExplorer";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

export default async function AgentsPage() {
  const dict = getDictionary(await getLocale());
  const [session, allAgents, branches] = await Promise.all([
    getSession(),
    api.get<AgentResponse[]>("/admin/agents"),
    api.get<BranchResponse[]>("/admin/branches"),
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
    const pct = escrow && escrow.effectiveCeilingXaf > 0 ? ceilingUtilizationPct(escrow.cumulativeTodayXaf, escrow.effectiveCeilingXaf) : null;
    return {
      id: agent.id,
      fullName: agent.fullName,
      employeeCode: agent.employeeCode,
      branchName: branchById.get(agent.branchId)?.name ?? "N/A",
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
        title={dict.agents.overviewTitle}
        subtitle={session?.role === "ADMIN" ? dict.agents.overviewSubtitleAdmin : dict.agents.overviewSubtitleBranch}
      />
      <AgentsExplorer
        rows={rows}
        actions={
          canCreate ? (
            <Link
              href="/registrations/new?from=/agents"
              className="inline-flex items-center justify-center gap-2 min-h-12 px-4 rounded-[var(--radius-md)] text-sm font-semibold bg-primary text-on-primary hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
            >
              <Icon name="plus" className="size-5" />
              {dict.registrations.newApplication}
            </Link>
          ) : undefined
        }
      />
    </div>
  );
}
