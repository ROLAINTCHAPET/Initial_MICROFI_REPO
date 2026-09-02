import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Badge } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import { BackLink } from "@/components/BackLink";
import type { AgentResponse, BranchResponse, ClientResponse } from "@/lib/types";
import { ClientTransactionsPanel } from "./ClientTransactionsPanel";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

function InfoField({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 text-sm">
      <span className="text-on-surface-variant">{label}</span>
      <span className="font-medium text-on-surface text-right">{value}</span>
    </div>
  );
}

export default async function ClientDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const dict = getDictionary(await getLocale());
  const { id } = await params;
  const [session, client, agents, branches] = await Promise.all([
    getSession(),
    api.get<ClientResponse>(`/admin/clients/${id}`),
    api.get<AgentResponse[]>("/admin/agents"),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);

  const branch = branches.find((b) => b.id === client.branchId);
  const agentNamesById: Record<string, string> = Object.fromEntries(
    agents.map((a) => [a.id, `${a.fullName} (${a.employeeCode})`])
  );

  return (
    <div className="max-w-4xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title={dict.clients.pageTitle} subtitle={dict.clients.pageSubtitle} />
      <BackLink href="/clients" label={dict.clients.backToClients} />

      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-6 shadow-sm flex flex-col gap-4">
        <div className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-4 min-w-0">
            <div className="w-12 h-12 rounded-full bg-primary-container/10 border-2 border-primary-container/20 flex items-center justify-center text-primary shrink-0">
              <Icon name="person" className="size-6" />
            </div>
            <h2 className="text-h2 text-on-surface truncate">{client.fullName}</h2>
          </div>
          <Badge status={client.status} />
        </div>
        <div className="space-y-3 pt-3 border-t border-outline-variant">
          <InfoField label={dict.clients.detail.memberNo} value={client.mfiMemberNo} />
          <InfoField label={dict.clients.detail.phone} value={client.phone} />
          <InfoField label={dict.clients.detail.branch} value={branch?.name ?? "N/A"} />
        </div>
      </div>

      <ClientTransactionsPanel
        clientId={client.id}
        clientLabel={`${client.fullName} (${client.mfiMemberNo})`}
        agentNamesById={agentNamesById}
        generatedBy={session?.sub ?? ""}
      />
    </div>
  );
}
