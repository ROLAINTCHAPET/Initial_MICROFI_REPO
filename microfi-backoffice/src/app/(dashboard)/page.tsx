import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { formatCompactXaf } from "@/lib/format";
import { PageHeader } from "@/components/PageHeaderContext";
import { Icon, type IconName } from "@/components/Icon";
import { Badge } from "@/components/Badge";
import { BranchSettingsModal } from "@/components/branches/BranchSettingsModal";
import { BranchesWorkspace } from "@/components/branches/BranchesWorkspace";
import { CreateBranchModal } from "@/components/branches/CreateBranchModal";
import type { BranchRow } from "@/components/branches/BranchDirectory";
import type { AgentResponse, BranchResponse, EscrowResponse, OfjPendingLineResponse, OfjSummaryResponse, ScheduleDefaultsResponse, SosResponse } from "@/lib/types";

function yesterdayBusinessDate(): string {
  return new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

export default async function RegionalDashboardPage() {
  const [session, allAgents, branches, scheduleDefaults] = await Promise.all([
    getSession(),
    api.get<AgentResponse[]>("/admin/agents"),
    api.get<BranchResponse[]>("/admin/branches"),
    api.get<ScheduleDefaultsResponse>("/admin/branches/schedule-defaults"),
  ]);

  // GET /admin/agents returns every agent network-wide regardless of caller role (unlike SOS, Team,
  // and OFJ, which are already scoped server-side) — so the branch manager/cashier view is scoped here.
  const agents = session?.role === "ADMIN" ? allAgents : allAgents.filter((a) => a.branchId === session?.branchId);
  const homeBranch = session?.role === "ADMIN" ? null : branches.find((b) => b.id === session?.branchId) ?? null;

  const agentById = new Map(agents.map((a) => [a.id, a]));
  const [escrows, unresolvedSos] = await Promise.all([
    Promise.all(agents.map((a) => api.get<EscrowResponse>(`/agents/${a.id}/escrow`).catch(() => null))),
    api.get<SosResponse[]>("/admin/sos-events?unresolvedOnly=true").catch(() => []),
  ]);

  const activeCount = agents.filter((a) => a.status === "ACTIVE").length;
  const totalEscrow = escrows.reduce((sum, e) => sum + (e?.balanceXaf ?? 0), 0);

  const nearCeiling = agents
    .map((agent, i) => ({ agent, escrow: escrows[i] }))
    .filter((x): x is { agent: AgentResponse; escrow: EscrowResponse } => !!x.escrow && x.escrow.effectiveCeilingXaf > 0)
    .map((x) => ({ ...x, pct: Math.round((x.escrow.balanceXaf / x.escrow.effectiveCeilingXaf) * 100) }))
    .filter((x) => x.pct >= 80)
    .sort((a, b) => b.pct - a.pct)
    .slice(0, 5);

  const visibleBranches = session?.role === "ADMIN" ? branches : branches.filter((b) => b.id === session?.branchId);
  const branchSummariesToday = await Promise.all(
    visibleBranches.map((b) => api.get<OfjSummaryResponse>(`/ofj/${b.id}/summary`).catch(() => null))
  );
  // "Today's Reconciliations" spans a rolling 48h window (today + yesterday's business date) so a
  // reconciliation done late yesterday, or just before a shift change, doesn't drop off the moment
  // the calendar date rolls over.
  const yesterdayDate = yesterdayBusinessDate();
  const branchSummariesYesterday = await Promise.all(
    visibleBranches.map((b) => api.get<OfjSummaryResponse>(`/ofj/${b.id}/summary?date=${yesterdayDate}`).catch(() => null))
  );
  const homeBranchSummary = homeBranch ? branchSummariesToday[0] ?? null : null;
  const pendingShortages = homeBranchSummary?.agentLines.filter((l) => !l.resolved).length ?? 0;
  // Most "awaiting reconciliation" agents haven't been touched yet today at all — an existing
  // OfjAgentLine only exists once someone has already reconciled them once (see cashier/page.tsx).
  const notYetReconciled = homeBranch ? await api.get<OfjPendingLineResponse[]>(`/ofj/${homeBranch.id}/pending`).catch(() => []) : [];
  const pendingReconciliation = pendingShortages + notYetReconciled.length;

  // Flattened per-agent reconciliation detail (not just a per-branch count) — a shortage or
  // still-open line is what a manager/admin actually needs to see and act on, not just a tally.
  const reconciliationRows = visibleBranches
    .flatMap((branch, i) => {
      const summaries = [branchSummariesToday[i], branchSummariesYesterday[i]].filter((s): s is OfjSummaryResponse => s !== null);
      return summaries.flatMap((summary) =>
        summary.agentLines.map((line) => ({
          lineId: `${summary.sessionId}-${line.id}`,
          branchName: branch.name,
          businessDate: summary.businessDate,
          agentLabel: agentById.get(line.agentId)
            ? `${agentById.get(line.agentId)!.fullName} (${agentById.get(line.agentId)!.employeeCode})`
            : line.agentId,
          digitalTotalXaf: line.digitalTotalXaf,
          physicalTotalXaf: line.physicalTotalXaf,
          deltaXaf: line.deltaXaf,
          resolved: line.resolved,
        }))
      );
    })
    .sort((a, b) => b.businessDate.localeCompare(a.businessDate));
  const showBranchColumn = visibleBranches.length > 1;

  const branchRows: BranchRow[] = branches.map((b) => ({
    id: b.id,
    code: b.code,
    name: b.name,
    phone: b.phone,
    timezone: b.timezone,
    openTime: b.openTime,
    closeTime: b.closeTime,
    maxCashiers: b.maxCashiers,
    requireImei: b.requireImei,
    defaultCeilingPct: b.defaultCeilingPct,
    canEdit: true, // this section only renders for ADMIN, who can edit every branch
  }));

  return (
    <div className="max-w-7xl mx-auto w-full flex flex-col gap-6">
      <PageHeader
        title={homeBranch ? "Branch Overview" : "Regional Overview"}
        subtitle={homeBranch ? `${homeBranch.name} (${homeBranch.code}) · Live Operational Metrics` : "CEMAC Region · Live Operational Metrics"}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard icon="agents" label="Total Agents" value={agents.length.toLocaleString()} />
        <StatCard icon="check-circle" label="Active Agents" value={activeCount.toLocaleString()} />
        <StatCard icon="lock" label="Total Escrow" value={formatCompactXaf(totalEscrow)} />
        <StatCard icon="bell" label="Unresolved SOS" value={unresolvedSos.length.toLocaleString()} alert={unresolvedSos.length > 0} href="/sos" />
      </div>

      {homeBranch && (
        <div className={`grid grid-cols-1 gap-4 ${session?.role === "BRANCH_CASHIER" ? "md:grid-cols-2" : ""}`}>
          <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-5 flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-center gap-6 flex-wrap">
              <div className="flex items-center gap-3">
                <div className="h-10 w-10 rounded-[var(--radius-sm)] flex items-center justify-center bg-primary-container/10 text-primary shrink-0">
                  <Icon name="schedule" className="size-5" />
                </div>
                <div>
                  <p className="text-xs text-on-surface-variant uppercase tracking-widest mb-1 font-semibold">Business Hours</p>
                  <p className="font-bold text-lg text-primary tabular-nums">
                    {homeBranch.openTime && homeBranch.closeTime ? (
                      `${homeBranch.openTime.slice(0, 5)} – ${homeBranch.closeTime.slice(0, 5)}`
                    ) : (
                      <span className="text-text-grey-disabled font-normal text-sm">Not configured</span>
                    )}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <div className="h-10 w-10 rounded-[var(--radius-sm)] flex items-center justify-center bg-primary-container/10 text-primary shrink-0">
                  <Icon name="phone" className="size-5" />
                </div>
                <div>
                  <p className="text-xs text-on-surface-variant uppercase tracking-widest mb-1 font-semibold">Contact Number</p>
                  <p className="font-bold text-lg text-primary tabular-nums">
                    {homeBranch.phone ?? <span className="text-text-grey-disabled font-normal text-sm">Not configured</span>}
                  </p>
                </div>
              </div>
            </div>
            {session?.role === "BRANCH_MANAGER" && (
              <BranchSettingsModal branchId={homeBranch.id} branchName={homeBranch.name} openTime={homeBranch.openTime} closeTime={homeBranch.closeTime} phone={homeBranch.phone} maxCashiers={homeBranch.maxCashiers} requireImei={homeBranch.requireImei} defaultCeilingPct={homeBranch.defaultCeilingPct} />
            )}
          </div>

          {session?.role === "BRANCH_CASHIER" && (
            <div
              className={`border-2 rounded-[var(--radius-md)] p-5 flex items-center justify-between gap-4 flex-wrap ${
                pendingReconciliation > 0 ? "bg-tertiary-fixed/20 border-tertiary-fixed-dim/60" : "bg-surface-container-lowest border-outline-variant"
              }`}
            >
              <div className="flex items-center gap-3">
                <div
                  className={`h-10 w-10 rounded-[var(--radius-sm)] flex items-center justify-center shrink-0 ${
                    pendingReconciliation > 0 ? "bg-tertiary-fixed text-on-tertiary-fixed-variant" : "bg-primary-container/10 text-primary"
                  }`}
                >
                  <Icon name="account-balance-wallet" className="size-5" />
                </div>
                <div>
                  <p className="text-xs text-on-surface-variant uppercase tracking-widest mb-1 font-semibold">Awaiting Reconciliation</p>
                  <p className={`font-bold text-lg tabular-nums ${pendingReconciliation > 0 ? "text-on-tertiary-fixed-variant" : "text-primary"}`}>
                    {pendingReconciliation} agent{pendingReconciliation === 1 ? "" : "s"}
                  </p>
                </div>
              </div>
              <Link
                href="/cashier"
                className="h-10 px-4 rounded-[var(--radius-sm)] bg-primary text-on-primary text-sm font-semibold flex items-center gap-2 transition-transform duration-150 ease-out hover:scale-[1.03] active:scale-95"
              >
                Open Cashier Portal
                <Icon name="chevron-right" className="size-4" />
              </Link>
            </div>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] overflow-hidden flex flex-col">
          <div className="p-4 border-b-2 border-outline-variant flex items-center justify-between">
            <h3 className="text-h2 text-primary">Agents Near Ceiling</h3>
            <Link href="/agents" className="text-sm text-primary hover:underline underline-offset-2 font-medium">View all</Link>
          </div>
          <div className="p-3 flex flex-col gap-2">
            {nearCeiling.map(({ agent, escrow, pct }) => (
              <Link
                key={agent.id}
                href={`/agents/${agent.id}`}
                className={`card-interactive rounded-[var(--radius-sm)] p-3 border-l-4 flex items-center justify-between gap-4 transition-colors ${
                  pct >= 100 ? "border-error bg-error-container/10 hover:bg-error-container/20" : "border-tertiary-fixed-dim bg-surface-container-low hover:bg-surface-container"
                }`}
              >
                <div>
                  <p className="font-semibold text-sm text-on-surface">{agent.fullName}</p>
                  <p className="text-xs text-on-surface-variant">{agent.employeeCode}</p>
                </div>
                <span className={`font-bold text-sm tabular-nums ${pct >= 100 ? "text-error" : "text-on-tertiary-fixed-variant"}`}>
                  {escrow.balanceXaf.toLocaleString()} XAF &middot; {pct}%
                </span>
              </Link>
            ))}
            {nearCeiling.length === 0 && <p className="p-6 text-center text-sm text-on-surface-variant">No agents currently near their escrow ceiling.</p>}
          </div>
        </div>

        <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] overflow-hidden flex flex-col">
          <div className="p-4 border-b-2 border-outline-variant flex items-center justify-between">
            <h3 className="text-h2 text-primary flex items-center gap-2">
              <Icon name="bell" filled className="size-5 text-error" />
              Unresolved SOS Alerts
            </h3>
            <Link href="/sos" className="text-sm text-primary hover:underline underline-offset-2 font-medium">Open console</Link>
          </div>
          <div className="p-3 flex flex-col gap-2">
            {unresolvedSos.slice(0, 5).map((event) => {
              const agent = agentById.get(event.agentId);
              return (
                <div key={event.id} className="rounded-[var(--radius-sm)] p-3 border-l-4 border-error bg-error-container/10">
                  <div className="flex justify-between items-start gap-4">
                    <p className="font-semibold text-sm text-on-surface">{agent ? `${agent.fullName} (${agent.employeeCode})` : event.agentId}</p>
                    <span className="text-xs text-on-surface-variant shrink-0">{new Date(event.raisedAt).toLocaleString()}</span>
                  </div>
                </div>
              );
            })}
            {unresolvedSos.length === 0 && <p className="p-6 text-center text-sm text-on-surface-variant">No unresolved SOS alerts.</p>}
          </div>
        </div>
      </div>

      <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] overflow-hidden">
        <div className="p-4 border-b-2 border-outline-variant flex items-center justify-between">
          <div>
            <h3 className="text-h2 text-primary">{homeBranch ? "Reconciliations" : "Branches · Reconciliations"}</h3>
            <p className="text-xs text-on-surface-variant mt-0.5">Last 48 hours</p>
          </div>
          <Link href="/ofj" className="text-sm text-primary hover:underline underline-offset-2 font-medium">
            Open End of Day Oversight
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-surface-container-low border-b-2 border-outline-variant text-xs text-on-surface-variant uppercase tracking-wider">
                <th className="p-4 font-semibold">Date</th>
                {showBranchColumn && <th className="p-4 font-semibold">Branch</th>}
                <th className="p-4 font-semibold">Agent</th>
                <th className="p-4 font-semibold">Digital Total</th>
                <th className="p-4 font-semibold">Physical Total</th>
                <th className="p-4 font-semibold">Delta</th>
                <th className="p-4 font-semibold">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant">
              {reconciliationRows.map((row) => {
                const problematic = !row.resolved;
                return (
                  <tr
                    key={row.lineId}
                    className={`transition-colors border-l-4 ${
                      problematic ? "border-l-danger-red bg-error-container/10 hover:bg-error-container/20" : "border-l-transparent hover:bg-surface-container-low/60"
                    }`}
                  >
                    <td className="p-4 text-sm text-on-surface-variant whitespace-nowrap">{row.businessDate}</td>
                    {showBranchColumn && <td className="p-4 text-sm text-on-surface-variant">{row.branchName}</td>}
                    <td className="p-4 font-medium text-sm text-on-surface">
                      <span className="flex items-center gap-1.5">
                        {problematic && <Icon name="warning" filled className="size-4 text-danger-red shrink-0" />}
                        {row.agentLabel}
                      </span>
                    </td>
                    <td className="p-4 text-sm text-on-surface-variant">{row.digitalTotalXaf.toLocaleString()} XAF</td>
                    <td className="p-4 text-sm text-on-surface-variant">{row.physicalTotalXaf.toLocaleString()} XAF</td>
                    <td className={`p-4 text-sm font-semibold ${row.deltaXaf < 0 ? "text-danger-red" : "text-secondary"}`}>
                      {row.deltaXaf.toLocaleString()} XAF
                    </td>
                    <td className="p-4">
                      <Badge status={row.resolved ? "RESOLVED" : "OPEN"} />
                    </td>
                  </tr>
                );
              })}
              {reconciliationRows.length === 0 && (
                <tr>
                  <td colSpan={showBranchColumn ? 7 : 6} className="p-8 text-center text-on-surface-variant">
                    No reconciliations recorded in the last 48 hours.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {session?.role === "ADMIN" && (
        <BranchesWorkspace
          scheduleDefaults={scheduleDefaults}
          editable
          branches={branchRows}
          actions={<CreateBranchModal />}
        />
      )}
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
  alert = false,
  href,
}: {
  icon: IconName;
  label: string;
  value: string;
  alert?: boolean;
  href?: string;
}) {
  const content = (
    <div
      className={`bg-surface-container-lowest border-2 rounded-[var(--radius-md)] p-5 flex flex-col gap-3 ${alert ? "border-error/40" : "border-outline-variant"} ${
        href ? "card-interactive cursor-pointer" : ""
      }`}
    >
      <div className={`h-10 w-10 rounded-[var(--radius-sm)] flex items-center justify-center ${alert ? "bg-error-container text-on-error-container" : "bg-primary-container/10 text-primary"}`}>
        <Icon name={icon} className="size-5" />
      </div>
      <div>
        <p className="text-xs text-on-surface-variant uppercase tracking-widest mb-1 font-semibold">{label}</p>
        <p className={`font-bold text-2xl tabular-nums ${alert ? "text-error" : "text-primary"}`}>{value}</p>
      </div>
    </div>
  );
  return href ? <Link href={href}>{content}</Link> : content;
}
