import Link from "next/link";
import { api } from "@/lib/api";
import { PageHeader } from "@/components/PageHeaderContext";
import { Icon, type IconName } from "@/components/Icon";
import type { AgentResponse, SosResponse } from "@/lib/types";
import { AcknowledgeButton } from "./AcknowledgeButton";
import { getDictionary, type Dictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { t } from "@/lib/i18n/format";

function timeAgo(iso: string, dict: Dictionary) {
  const ms = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(ms / 60000);
  if (mins < 1) return dict.sos.timeAgo.justNow;
  if (mins < 60) return t(dict.sos.timeAgo.minutesAgo, { minutes: mins });
  const hours = Math.floor(mins / 60);
  if (hours < 24) return t(dict.sos.timeAgo.hoursAgo, { hours });
  return t(dict.sos.timeAgo.daysAgo, { days: Math.floor(hours / 24) });
}

export default async function SosConsolePage({
  searchParams,
}: {
  searchParams: Promise<{ unresolvedOnly?: string }>;
}) {
  const dict = getDictionary(await getLocale());
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
      <PageHeader title={dict.sidebar.sosConsole} subtitle={dict.sos.subtitle} />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard icon="bell" label={dict.sos.totalAlerts} value={allEvents.length.toLocaleString()} />
        <StatCard icon="warning" label={dict.sos.unresolved} value={unresolvedCount.toLocaleString()} alert={unresolvedCount > 0} />
        <StatCard icon="check-circle" label={dict.common.status.ACKNOWLEDGED} value={acknowledgedCount.toLocaleString()} />
      </div>

      <div className="flex justify-end">
        <Link href={`/sos?unresolvedOnly=${!unresolvedOnly}`} className="text-sm text-primary hover:underline underline-offset-2 font-medium">
          {unresolvedOnly ? dict.sos.showAllAlerts : dict.sos.showUnresolvedOnly}
        </Link>
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b-2 border-outline-variant bg-surface-bright">
          <div className="flex items-center gap-2 font-bold text-on-surface">
            <Icon name="bell" filled className="size-5 text-danger-red" />
            {dict.sos.criticalAlerts}
          </div>
          {unresolvedCount > 0 && (
            <span className="inline-flex items-center px-2.5 py-1 rounded-[var(--radius-full)] bg-danger-red text-white text-xs font-bold">
              {t(dict.sos.newBadge, { count: unresolvedCount })}
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
                    <span className="text-xs text-text-slate shrink-0">{timeAgo(event.raisedAt, dict)}</span>
                  </div>
                  <p className="text-sm text-text-slate mt-1 flex items-center gap-1.5">
                    <Icon name="location" className="size-4 shrink-0" />
                    {event.lat !== null && event.lon !== null ? `${event.lat.toFixed(5)}, ${event.lon.toFixed(5)}` : dict.sos.locationUnavailable}
                  </p>
                  <p className="text-xs text-text-grey-disabled mt-1">{new Date(event.raisedAt).toLocaleString()}</p>
                  <div className="mt-3 flex items-center gap-3">
                    {resolved ? (
                      <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-on-surface-variant">
                        <Icon name="check-circle" filled className="size-4 text-secondary" />
                        {dict.common.status.ACKNOWLEDGED}
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
            {unresolvedOnly ? dict.sos.noUnresolvedAlerts : dict.sos.noAlerts}
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
