import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Icon } from "@/components/Icon";
import { EmptyState } from "@/components/Table";
import type { AgentResponse } from "@/lib/types";
import { AgentGeofenceEditor } from "../AgentGeofenceEditor";
import { BackLink } from "@/components/BackLink";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

export default async function AgentGeofencePage({ params }: { params: Promise<{ id: string }> }) {
  const dict = getDictionary(await getLocale());
  const { id } = await params;
  const [session, agent] = await Promise.all([getSession(), api.get<AgentResponse>(`/admin/agents/${id}`)]);

  const canManage = session?.role === "ADMIN" || (session?.role === "BRANCH_MANAGER" && session?.branchId === agent.branchId);
  if (!canManage || agent.status === "DELETED") {
    return <EmptyState>{dict.settings.accessDenied}</EmptyState>;
  }

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-4">
      <BackLink href={`/agents/${agent.id}?tab=administration`} label={dict.agents.detail.backToAgent} />

      <div className="flex items-center text-xs text-on-surface-variant gap-2">
        <Link href="/agents" className="hover:text-primary transition-colors">{dict.agents.detail.breadcrumbAgents}</Link>
        <Icon name="chevron-right" className="size-4" />
        <Link href={`/agents/${agent.id}?tab=administration`} className="hover:text-primary transition-colors">{agent.employeeCode}</Link>
        <Icon name="chevron-right" className="size-4" />
        <span className="text-on-surface font-semibold">{dict.tracking.workspace.configureGeofence}</span>
      </div>
      <PageHeader title={dict.tracking.workspace.configureGeofence} subtitle={`${agent.fullName} · ${agent.employeeCode}`} />
      <AgentGeofenceEditor agentId={agent.id} agentLabel={`${agent.fullName} (${agent.employeeCode})`} />
    </div>
  );
}
