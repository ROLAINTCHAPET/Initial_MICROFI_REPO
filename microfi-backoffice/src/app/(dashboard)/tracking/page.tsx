import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { AutoRefresh } from "@/components/AutoRefresh";
import { EmptyState } from "@/components/Table";
import type { AgentResponse, BranchResponse, EscrowResponse, GeofenceAlertResponse, RouteResponse } from "@/lib/types";
import { TrackingWorkspace, type TrackingAgent } from "./TrackingWorkspace";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

export default async function TrackingPage() {
  const dict = getDictionary(await getLocale());
  const session = await getSession();

  // A cashier reconciles cash, not field movement — live tracking is an oversight tool for
  // ADMIN/BRANCH_MANAGER only, same restriction as Settings and Registrations.
  if (session?.role === "BRANCH_CASHIER") {
    return <EmptyState>{dict.tracking.page.accessDenied}</EmptyState>;
  }

  const [allAgents, branches] = await Promise.all([
    api.get<AgentResponse[]>("/admin/agents"),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);

  // Same branch-scoping gap as Dashboard/Agents Overview: GET /admin/agents is network-wide.
  // Suspended agents aren't in the field, so they're excluded from live tracking entirely.
  const agents = (session?.role === "ADMIN" ? allAgents : allAgents.filter((a) => a.branchId === session?.branchId)).filter(
    (a) => a.status !== "SUSPENDED"
  );
  const branchById = new Map(branches.map((b) => [b.id, b]));
  const today = new Date().toISOString().slice(0, 10);

  const [escrows, routes, alertLists] = await Promise.all([
    Promise.all(agents.map((a) => api.get<EscrowResponse>(`/agents/${a.id}/escrow`).catch(() => null))),
    Promise.all(agents.map((a) => api.get<RouteResponse>(`/admin/agents/${a.id}/route?date=${today}`).catch(() => null))),
    Promise.all(agents.map((a) => api.get<GeofenceAlertResponse[]>(`/admin/agents/${a.id}/geofence-alerts`).catch(() => []))),
  ]);

  const trackingAgents: TrackingAgent[] = agents
    .map((agent, i) => {
      const points = routes[i]?.points ?? [];
      const transactions = routes[i]?.transactions ?? [];
      const lastCollectionAt =
        transactions.length > 0
          ? transactions.reduce((latest, t) => (t.collectedAt > latest ? t.collectedAt : latest), transactions[0].collectedAt)
          : null;
      return {
        id: agent.id,
        fullName: agent.fullName,
        employeeCode: agent.employeeCode,
        phone: agent.phone,
        branchName: branchById.get(agent.branchId)?.name ?? "N/A",
        status: agent.status,
        balanceXaf: escrows[i]?.balanceXaf ?? null,
        lastPingAt: points.length > 0 ? points[points.length - 1].recordedAt : null,
        lastCollectionAt,
        hasActiveAlert: alertLists[i]?.some((a) => a.active) ?? false,
      };
    })
    // Agents who've just collected surface first (most recent collection first), everyone else follows.
    .sort((a, b) => {
      if (a.lastCollectionAt && b.lastCollectionAt) return b.lastCollectionAt.localeCompare(a.lastCollectionAt);
      if (a.lastCollectionAt) return -1;
      if (b.lastCollectionAt) return 1;
      return 0;
    });

  return (
    <div className="max-w-7xl mx-auto w-full h-full flex flex-col gap-6">
      <AutoRefresh />
      <PageHeader title={dict.sidebar.geolocation} subtitle={dict.tracking.page.subtitle} />
      <TrackingWorkspace agents={trackingAgents} />
    </div>
  );
}
