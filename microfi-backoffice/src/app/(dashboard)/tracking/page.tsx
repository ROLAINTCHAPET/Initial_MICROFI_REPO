import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import type { AgentResponse, BranchResponse, EscrowResponse, GeofenceAlertResponse, RouteResponse } from "@/lib/types";
import { TrackingWorkspace, type TrackingAgent } from "./TrackingWorkspace";

export default async function TrackingPage() {
  const [session, allAgents, branches] = await Promise.all([
    getSession(),
    api.get<AgentResponse[]>("/admin/agents"),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);

  // Same branch-scoping gap as Dashboard/Agents Overview: GET /admin/agents is network-wide.
  const agents = session?.role === "ADMIN" ? allAgents : allAgents.filter((a) => a.branchId === session?.branchId);
  const branchById = new Map(branches.map((b) => [b.id, b]));
  const today = new Date().toISOString().slice(0, 10);

  const [escrows, routes, alertLists] = await Promise.all([
    Promise.all(agents.map((a) => api.get<EscrowResponse>(`/agents/${a.id}/escrow`).catch(() => null))),
    Promise.all(agents.map((a) => api.get<RouteResponse>(`/admin/agents/${a.id}/route?date=${today}`).catch(() => null))),
    Promise.all(agents.map((a) => api.get<GeofenceAlertResponse[]>(`/admin/agents/${a.id}/geofence-alerts`).catch(() => []))),
  ]);

  const trackingAgents: TrackingAgent[] = agents.map((agent, i) => {
    const points = routes[i]?.points ?? [];
    return {
      id: agent.id,
      fullName: agent.fullName,
      employeeCode: agent.employeeCode,
      phone: agent.phone,
      branchName: branchById.get(agent.branchId)?.name ?? "—",
      status: agent.status,
      balanceXaf: escrows[i]?.balanceXaf ?? null,
      lastPingAt: points.length > 0 ? points[points.length - 1].recordedAt : null,
      hasActiveAlert: alertLists[i]?.some((a) => a.active) ?? false,
    };
  });

  const canEditGeofence = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";

  return (
    <div className="max-w-7xl mx-auto w-full h-full flex flex-col gap-6">
      <PageHeader title="Geolocation" subtitle="Live field units, historical routes, and geofence boundaries." />
      <TrackingWorkspace agents={trackingAgents} canEditGeofence={canEditGeofence} />
    </div>
  );
}
