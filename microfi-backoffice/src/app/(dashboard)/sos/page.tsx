import Link from "next/link";
import { api } from "@/lib/api";
import { PageHeader } from "@/components/PageHeaderContext";
import { Icon, type IconName } from "@/components/Icon";
import type { AgentResponse, SosResponse } from "@/lib/types";
import { AcknowledgeButton } from "./AcknowledgeButton";

function timeAgo(iso: string) {
  const ms = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(ms / 60000);
  if (mins < 1) return "Just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

export default async function SosConsolePage({
  searchParams,
}: {
  searchParams: Promise<{ unresolvedOnly?: string }>;
}) {
  const params = await searchParams;
  const unresolvedOnly = params.unresolvedOnly !== "false";

  const [events, allEvents, agents] = await Promise.all([
    api.get<SosResponse[]>(`/admin/sos-events?unresolvedOnly=${unresolvedOnly}`),
    api.get<SosResponse[]>("/admin/sos-events?unresolvedOnly=false"),
    api.get<AgentResponse[]>("/admin/agents"),
  ]);
  const agentById = new Map(agents.map((a) => [a.id, a]));
  const unresolvedCount = allEvents.filter((e) => e.acknowledgedAt === null).length;
  const acknowledgedCount = allEvents.length - unresolvedCount;

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title="SOS Console" subtitle="Distress alerts raised by field agents." />

      <div className="grid grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard icon="bell" label="Total Alerts" value={allEvents.length.toLocaleString()} />
        <StatCard icon="warning" label="Unresolved" value={unresolvedCount.toLocaleString()} alert={unresolvedCount > 0} />
        <StatCard icon="check-circle" label="Acknowledged" value={acknowledgedCount.toLocaleString()} />
      </div>

      <div className="flex justify-end">
        <Link href={`/sos?unresolvedOnly=${!unresolvedOnly}`} className="text-sm text-primary hover:underline underline-offset-2 font-medium">
          {unresolvedOnly ? "Show all alerts" : "Show unresolved only"}
        </Link>
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b-2 border-outline-variant bg-surface-bright">
          <div className="flex items-center gap-2 font-bold text-on-surface">
            <Icon name="bell" filled className="size-5 text-danger-red" />
            Critical Alerts
          </div>
          {unresolvedCount > 0 && (
            <span className="inline-flex items-center px-2.5 py-1 rounded-[var(--radius-full)] bg-danger-red text-white text-xs font-bold">
              {unresolvedCount} New
            </span>
          )}
        </div>

        <div className="divide-y divide-outline-variant">
          {events.map((event) => {
            const agent = agentById.get(event.agentId);
            const resolved = event.acknowledgedAt !== null;
            return (
              <div key={event.id} className={`card-interactive flex items-start gap-4 p-5 border-l-4 ${resolved ? "border-l-outline-variant" : "border-l-danger-red bg-error-container/10"}`}>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-4">
                    <p className="font-semibold text-on-surface">{agent ? `${agent.fullName} (${agent.employeeCode})` : event.agentId}</p>
                    <span className="text-xs text-text-slate shrink-0">{timeAgo(event.raisedAt)}</span>
                  </div>
                  <p className="text-sm text-text-slate mt-1 flex items-center gap-1.5">
                    <Icon name="location" className="size-4 shrink-0" />
                    {event.lat !== null && event.lon !== null ? `${event.lat.toFixed(5)}, ${event.lon.toFixed(5)}` : "Location unavailable"}
                  </p>
                  <p className="text-xs text-text-grey-disabled mt-1">{new Date(event.raisedAt).toLocaleString()}</p>
                  <div className="mt-3 flex items-center gap-3">
                    {resolved ? (
                      <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-on-surface-variant">
                        <Icon name="check-circle" filled className="size-4 text-secondary" />
                        Acknowledged
                      </span>
                    ) : (
                      <AcknowledgeButton eventId={event.id} />
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
        {events.length === 0 && (
          <div className="p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
            <Icon name="check-circle" className="size-6 text-outline-variant" />
            No {unresolvedOnly ? "unresolved " : ""}SOS alerts.
          </div>
        )}
      </div>
    </div>
  );
}

function StatCard({ icon, label, value, alert = false }: { icon: IconName; label: string; value: string; alert?: boolean }) {
  return (
    <div className={`bg-surface-container-lowest border-2 rounded-[var(--radius-md)] p-5 flex flex-col gap-3 ${alert ? "border-error/40" : "border-outline-variant"}`}>
      <div className={`h-10 w-10 rounded-[var(--radius-sm)] flex items-center justify-center ${alert ? "bg-error-container text-on-error-container" : "bg-primary-container/10 text-primary"}`}>
        <Icon name={icon} className="size-5" />
      </div>
      <div>
        <p className="text-xs text-on-surface-variant uppercase tracking-widest mb-1 font-semibold">{label}</p>
        <p className={`font-bold text-2xl tabular-nums ${alert ? "text-error" : "text-primary"}`}>{value}</p>
      </div>
    </div>
  );
}
