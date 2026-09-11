import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { AutoRefresh } from "@/components/AutoRefresh";
import { Table, Thead, Th, Tbody, Tr, Td, EmptyState } from "@/components/Table";
import type { BranchResponse, BroadcastMessageResponse } from "@/lib/types";
import { BroadcastComposer } from "./BroadcastComposer";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

export default async function BroadcastsPage() {
  const dict = getDictionary(await getLocale());
  const session = await getSession();

  if (session?.role !== "ADMIN" && session?.role !== "BRANCH_MANAGER") {
    return <EmptyState>{dict.settings.accessDenied}</EmptyState>;
  }

  const [history, branches] = await Promise.all([
    api.get<BroadcastMessageResponse[]>("/admin/broadcasts").catch(() => []),
    session.role === "ADMIN" ? api.get<BranchResponse[]>("/admin/branches") : Promise.resolve([]),
  ]);
  const branchById = new Map(branches.map((b) => [b.id, b]));
  const ownBranch = branches.find((b) => b.id === session.branchId);

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-6">
      <AutoRefresh />
      <PageHeader title={dict.sidebar.broadcasts} subtitle={dict.broadcasts.pageSubtitle} />

      <BroadcastComposer role={session.role} branches={branches} ownBranchLabel={ownBranch ? `${ownBranch.name} (${ownBranch.code})` : null} />

      <div className="flex flex-col gap-3">
        <h3 className="text-h2 text-primary">{dict.broadcasts.historyTitle}</h3>
        <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden overflow-x-auto">
          <Table>
            <Thead>
              <Th>{dict.broadcasts.colAudience}</Th>
              <Th>{dict.broadcasts.colScope}</Th>
              <Th>{dict.broadcasts.colMessage}</Th>
              <Th>{dict.broadcasts.colSentBy}</Th>
              <Th>{dict.broadcasts.colSentAt}</Th>
              <Th>{dict.broadcasts.colRecipients}</Th>
            </Thead>
            <Tbody>
              {history.map((m) => {
                const branch = m.branchId ? branchById.get(m.branchId) : null;
                const scopeLabel = m.branchId === null ? dict.broadcasts.networkWide : branch ? `${branch.name} (${branch.code})` : m.branchId;
                return (
                  <Tr key={m.id}>
                    <Td className="font-medium text-on-surface">{m.audience === "CLIENTS" ? dict.broadcasts.audienceClients : dict.broadcasts.audienceAgents}</Td>
                    <Td className="text-on-surface-variant">{scopeLabel}</Td>
                    <Td className="text-on-surface-variant max-w-[320px]">{m.message}</Td>
                    <Td className="text-on-surface-variant">{m.senderLabel}</Td>
                    <Td className="text-on-surface-variant whitespace-nowrap">{new Date(m.createdAt).toLocaleString()}</Td>
                    <Td className="text-on-surface-variant">{m.recipientCount ?? "—"}</Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
          {history.length === 0 && <EmptyState>{dict.broadcasts.noHistory}</EmptyState>}
        </div>
      </div>
    </div>
  );
}
