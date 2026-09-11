import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { AutoRefresh } from "@/components/AutoRefresh";
import { Table, Thead, Th, Tbody, Tr, Td, EmptyState } from "@/components/Table";
import { Badge } from "@/components/Badge";
import type { AgentMisconductReportResponse, AgentResponse, ClientResponse } from "@/lib/types";
import { MarkReviewedButton } from "./MarkReviewedButton";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

type StatusFilter = "PENDING" | "REVIEWED" | "ALL";

export default async function MisconductReportsPage({
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
  const status: StatusFilter = params.status === "REVIEWED" || params.status === "ALL" ? params.status : "PENDING";
  const query = status === "ALL" ? "" : `?unresolvedOnly=${status === "PENDING"}`;

  const [reports, agents] = await Promise.all([
    api.get<AgentMisconductReportResponse[]>(`/admin/misconduct-reports${query}`),
    api.get<AgentResponse[]>("/admin/agents"),
  ]);
  const agentById = new Map(agents.map((a) => [a.id, a]));

  // No unrestricted "list every client" endpoint exists (unlike agents) — each reporting
  // client is resolved individually. Report volume is expected to be low, so N+1 here is fine.
  const uniqueClientIds = Array.from(new Set(reports.map((r) => r.clientId)));
  const clients = await Promise.all(
    uniqueClientIds.map((id) => api.get<ClientResponse>(`/admin/clients/${id}`).catch(() => null))
  );
  const clientById = new Map(clients.filter((c): c is ClientResponse => c !== null).map((c) => [c.id, c]));

  const FILTERS: { key: StatusFilter; label: string }[] = [
    { key: "PENDING", label: dict.misconductReports.filterPending },
    { key: "REVIEWED", label: dict.misconductReports.filterReviewed },
    { key: "ALL", label: dict.misconductReports.filterAll },
  ];

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-4">
      <AutoRefresh />
      <PageHeader title={dict.sidebar.misconductReports} subtitle={dict.misconductReports.subtitle} />

      <div className="flex items-center gap-2">
        {FILTERS.map((f) => (
          <Link
            key={f.key}
            href={`/misconduct-reports?status=${f.key}`}
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
            <Th>{dict.misconductReports.colAgent}</Th>
            <Th>{dict.misconductReports.colClient}</Th>
            <Th>{dict.misconductReports.colReason}</Th>
            <Th>{dict.misconductReports.colReportedAt}</Th>
            <Th>{dict.misconductReports.colStatus}</Th>
            <Th></Th>
          </Thead>
          <Tbody>
            {reports.map((r) => {
              const agent = agentById.get(r.agentId);
              const agentLabel = agent ? `${agent.fullName} (${agent.employeeCode})` : r.agentId;
              const client = clientById.get(r.clientId);
              const clientLabel = client ? client.fullName : r.clientId;
              return (
                <Tr key={r.id}>
                  <Td className="font-medium text-on-surface">{agentLabel}</Td>
                  <Td className="text-on-surface-variant">{clientLabel}</Td>
                  <Td className="text-on-surface-variant max-w-[320px]">{r.reason}</Td>
                  <Td className="text-on-surface-variant whitespace-nowrap">{new Date(r.reportedAt).toLocaleString()}</Td>
                  <Td>
                    <Badge status={r.status === "PENDING" ? "PENDING" : "ACKNOWLEDGED"} />
                  </Td>
                  <Td>{r.status === "PENDING" && <MarkReviewedButton reportId={r.id} />}</Td>
                </Tr>
              );
            })}
          </Tbody>
        </Table>
        {reports.length === 0 && <EmptyState>{dict.misconductReports.noResults}</EmptyState>}
      </div>
    </div>
  );
}
