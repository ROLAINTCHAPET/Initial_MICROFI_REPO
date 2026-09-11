import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { EmptyState } from "@/components/Table";
import { Icon } from "@/components/Icon";
import type { AgentResponse, BranchResponse, ClientResponse } from "@/lib/types";
import { BranchSelector } from "./BranchSelector";
import { ClientsExplorer } from "./ClientsExplorer";
import { AllClientsTransactionsExport } from "./AllClientsTransactionsExport";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

// Any Back-Office role can look up a client (matches GET /admin/clients's own authorization —
// no role restriction beyond branch scope) — a cashier reconciling cash may need to confirm a
// client's transaction history just as much as a manager or admin does.
export default async function ClientsPage({
  searchParams,
}: {
  searchParams: Promise<{ branchId?: string }>;
}) {
  const dict = getDictionary(await getLocale());
  const [session, branches] = await Promise.all([getSession(), api.get<BranchResponse[]>("/admin/branches")]);
  const params = await searchParams;
  const branchId = session?.role === "ADMIN" ? (params.branchId ?? branches[0]?.id) : (session?.branchId ?? branches[0]?.id);

  if (!branchId) {
    return <EmptyState>{dict.clients.noBranches}</EmptyState>;
  }

  const branch = branches.find((b) => b.id === branchId);
  const [clients, agents] = await Promise.all([
    api.get<ClientResponse[]>(`/admin/clients?branchId=${branchId}`),
    api.get<AgentResponse[]>("/admin/agents"),
  ]);
  const agentNamesById: Record<string, string> = Object.fromEntries(agents.map((a) => [a.id, a.fullName]));
  const scope = branch ? `${branch.name} (${branch.code})` : dict.clients.branchFallback;

  return (
    <div className="max-w-4xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title={dict.clients.pageTitle} subtitle={dict.clients.pageSubtitle} />

      <div className="flex items-center justify-between gap-4 flex-wrap">
        {session?.role === "ADMIN" ? (
          <div className="flex items-center gap-3 flex-wrap w-full sm:w-auto">
            <span className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant shrink-0">
              <Icon name="location-on" className="size-5 text-primary" />
              {dict.clients.viewingBranch}
            </span>
            <BranchSelector branches={branches} selectedBranchId={branchId} />
          </div>
        ) : (
          <div className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant">
            <Icon name="location-on" className="size-5 text-primary" />
            {scope}
          </div>
        )}
        {/* Matches POST /admin/clients's own authorization (ADMIN or BRANCH_MANAGER, own branch) — a cashier can look clients up but not seed new ones. */}
        {(session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER") && (
          <Link
            href="/clients/new"
            className="inline-flex items-center justify-center gap-2 min-h-12 px-4 rounded-[var(--radius-md)] text-sm font-semibold bg-primary text-on-primary hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98] w-full sm:w-auto"
          >
            <Icon name="plus" className="size-5" />
            {dict.clients.createModal.title}
          </Link>
        )}
      </div>

      <ClientsExplorer clients={clients} scope={scope} generatedBy={session?.sub ?? ""} />

      <AllClientsTransactionsExport
        branchId={branchId}
        scope={scope}
        agentNamesById={agentNamesById}
        generatedBy={session?.sub ?? ""}
      />
    </div>
  );
}
