import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { EmptyState } from "@/components/Table";
import { Icon } from "@/components/Icon";
import type { AdminUserResponse, AgentResponse, BranchResponse } from "@/lib/types";
import { TeamDirectory, type TeamRow } from "./TeamDirectory";
import { TeamBranchSelector } from "./TeamBranchSelector";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { t } from "@/lib/i18n/format";

export default async function TeamPage({
  searchParams,
}: {
  searchParams: Promise<{ branchId?: string }>;
}) {
  const dict = getDictionary(await getLocale());
  const [session, users, agents, branches] = await Promise.all([
    getSession(),
    api.get<AdminUserResponse[]>("/admin/users"),
    api.get<AgentResponse[]>("/admin/agents"),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);
  const params = await searchParams;
  const branchId = session?.role === "ADMIN" ? params.branchId ?? branches[0]?.id : session?.branchId ?? branches[0]?.id;

  if (!branchId) {
    return <EmptyState>{dict.team.noBranchesYet}</EmptyState>;
  }

  const branch = branches.find((b) => b.id === branchId);
  const branchById = new Map(branches.map((b) => [b.id, b]));
  const canCreate = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";

  // Who sees whom mirrors the reporting line, not just "everyone in the branch": a manager
  // oversees their branch's cashiers and field agents; a cashier only needs the agents whose cash
  // they reconcile, not their manager or peer cashiers. ADMIN accounts are global (no branchId),
  // so they stay visible to ADMIN regardless of which branch is selected.
  const backOfficeRows: TeamRow[] = users
    .filter((u) => {
      if (session?.role === "ADMIN") return u.role === "ADMIN" || u.branchId === branchId;
      if (session?.role === "BRANCH_MANAGER") return u.role === "BRANCH_CASHIER" && u.branchId === branchId;
      return false; // BRANCH_CASHIER: no back-office accounts, agents only
    })
    .map((u) => ({
      id: u.id,
      href: `/team/${u.id}`,
      login: u.login,
      role: u.role,
      branchName: u.branchId ? (branchById.get(u.branchId)?.name ?? "N/A") : null,
      status: u.status,
    }));

  // GET /admin/agents is network-wide regardless of caller role (see agents/page.tsx), so the
  // branch scope is applied here the same way — filtering to whichever branch is being viewed.
  const agentRows: TeamRow[] = agents
    .filter((a) => a.branchId === branchId)
    .map((a) => ({
      id: a.id,
      href: `/agents/${a.id}`,
      login: `${a.fullName} (${a.employeeCode})`,
      role: "AGENT" as const,
      branchName: branchById.get(a.branchId)?.name ?? "N/A",
      status: a.status,
    }));

  const rows: TeamRow[] = [...backOfficeRows, ...agentRows];

  const subtitle =
    session?.role === "BRANCH_CASHIER"
      ? dict.team.subtitleCashier
      : session?.role === "BRANCH_MANAGER"
        ? dict.team.subtitleManager
        : dict.team.subtitleAdmin;

  return (
    <div className="max-w-5xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title={dict.sidebar.team} subtitle={subtitle} />

      <div className="flex items-center justify-between flex-wrap gap-4">
        {session?.role === "ADMIN" ? (
          <div className="flex items-center gap-3 flex-wrap w-full sm:w-auto">
            <span className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant shrink-0">
              <Icon name="location-on" className="size-5 text-primary" />
              {dict.team.viewingBranch}
            </span>
            <TeamBranchSelector branches={branches} selectedBranchId={branchId} />
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant">
            <Icon name="location-on" className="size-5 text-primary" />
            {branch ? t(dict.team.branchNameCode, { name: branch.name, code: branch.code }) : dict.team.branchFallback}
          </div>
        )}
      </div>

      <TeamDirectory
        rows={rows}
        scope={branch ? `${branch.name} (${branch.code})` : dict.team.branchFallback}
        generatedBy={session?.sub ?? ""}
        actions={
          canCreate ? (
            <Link
              href="/registrations/new?from=/team"
              className="inline-flex items-center justify-center gap-2 min-h-12 px-4 rounded-[var(--radius-md)] text-sm font-semibold bg-primary text-on-primary hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
            >
              <Icon name="plus" className="size-5" />
              {dict.team.newUser}
            </Link>
          ) : undefined
        }
      />
    </div>
  );
}
