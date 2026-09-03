import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Table, Thead, Th, Tbody, Tr, Td, EmptyState } from "@/components/Table";
import { Badge } from "@/components/Badge";
import type { AgentResponse, CollectionRejectionRequestResponse } from "@/lib/types";
import { ApproveRejectionModal } from "./ApproveRejectionModal";
import { DenyRejectionButton } from "./DenyRejectionButton";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

type StatusFilter = "PENDING" | "APPROVED" | "DENIED" | "ALL";

export default async function CollectionRejectionsPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>;
}) {
  const dict = getDictionary(await getLocale());
  const session = await getSession();

  if (session?.role !== "ADMIN" && session?.role !== "BRANCH_MANAGER") {
    return <EmptyState>{dict.settings.accessDenied}</EmptyState>;
  }

  const params = await searchParams;
  const status: StatusFilter = params.status === "APPROVED" || params.status === "DENIED" || params.status === "ALL" ? params.status : "PENDING";
  const query = status === "ALL" ? "" : `?status=${status}`;

  const [requests, agents] = await Promise.all([
    api.get<CollectionRejectionRequestResponse[]>(`/admin/collection-rejection-requests${query}`),
    api.get<AgentResponse[]>("/admin/agents"),
  ]);
  const agentById = new Map(agents.map((a) => [a.id, a]));

  const FILTERS: { key: StatusFilter; label: string }[] = [
    { key: "PENDING", label: dict.collectionRejections.filterPending },
    { key: "APPROVED", label: dict.collectionRejections.filterApproved },
    { key: "DENIED", label: dict.collectionRejections.filterDenied },
    { key: "ALL", label: dict.collectionRejections.filterAll },
  ];

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-4">
      <PageHeader title={dict.sidebar.collectionRejections} subtitle={dict.collectionRejections.subtitle} />

      <div className="flex items-center gap-2">
        {FILTERS.map((f) => (
          <Link
            key={f.key}
            href={`/collection-rejections?status=${f.key}`}
            className={`px-3 py-1.5 rounded-[var(--radius-full)] text-xs font-bold transition-colors ${
              status === f.key
                ? "bg-primary text-on-primary"
                : "border-2 border-outline-variant text-primary hover:bg-surface-container-low"
            }`}
          >
            {f.label}
          </Link>
        ))}
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden overflow-x-auto">
        <Table>
          <Thead>
            <tr>
              <Th>{dict.collectionRejections.colAgent}</Th>
              <Th>{dict.collectionRejections.colReason}</Th>
              <Th>{dict.collectionRejections.colRequestedAt}</Th>
              <Th>{dict.collectionRejections.colStatus}</Th>
              <Th>{dict.collectionRejections.colDecision}</Th>
              <Th></Th>
            </tr>
          </Thead>
          <Tbody>
            {requests.map((r) => {
              const agent = agentById.get(r.agentId);
              const agentLabel = agent ? `${agent.fullName} (${agent.employeeCode})` : r.agentId;
              return (
                <Tr key={r.id}>
                  <Td className="font-medium text-on-surface">{agentLabel}</Td>
                  <Td className="text-on-surface-variant max-w-[280px]">{r.reason}</Td>
                  <Td className="text-on-surface-variant whitespace-nowrap">{new Date(r.requestedAt).toLocaleString()}</Td>
                  <Td>
                    <Badge status={r.status} />
                  </Td>
                  <Td className="text-on-surface-variant max-w-[220px]">
                    {r.decisionReason ? `${dict.collectionRejections.decisionReasonPrefix}${r.decisionReason}` : "—"}
                  </Td>
                  <Td>
                    {r.status === "PENDING" && (
                      <div className="flex items-center gap-2">
                        <ApproveRejectionModal requestId={r.id} agentLabel={agentLabel} reason={r.reason} />
                        <DenyRejectionButton requestId={r.id} />
                      </div>
                    )}
                  </Td>
                </Tr>
              );
            })}
          </Tbody>
        </Table>
        {requests.length === 0 && <EmptyState>{dict.collectionRejections.noResults}</EmptyState>}
      </div>
    </div>
  );
}
