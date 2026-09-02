import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Badge, type BadgeStatus } from "@/components/Badge";
import { Icon, type IconName } from "@/components/Icon";
import type { BranchResponse, RegistrationApplicationResponse, RegistrationApplicationStatus } from "@/lib/types";
import { RegistrationsExportButtons, type RegistrationExportRow } from "./RegistrationsExportButtons";
import { ApproveButton } from "./ApproveButton";
import { RejectApplicationModal } from "./RejectApplicationModal";
import { DateRangeFilter } from "./DateRangeFilter";
import { getDictionary, type Dictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { t } from "@/lib/i18n/format";

function isoDaysAgo(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

const STATUS_BADGE: Record<RegistrationApplicationStatus, BadgeStatus> = {
  SUBMITTED: "PENDING",
  APPROVED: "ACTIVE",
  REJECTED: "SUSPENDED",
};

function roleLabel(dict: Dictionary): Record<string, string> {
  return {
    AGENT: dict.agents.fieldAgentLabel,
    BRANCH_MANAGER: dict.roles.BRANCH_MANAGER,
    BRANCH_CASHIER: dict.roles.BRANCH_CASHIER,
  };
}

export default async function RegistrationsPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; from?: string; to?: string }>;
}) {
  const dict = getDictionary(await getLocale());
  const ROLE_LABEL = roleLabel(dict);
  const params = await searchParams;
  const statusFilter = params.status === "APPROVED" || params.status === "REJECTED" ? params.status : "SUBMITTED";
  const from = params.from ?? isoDaysAgo(30);
  const to = params.to ?? isoDaysAgo(0);

  const [session, applications, allApplications, branches] = await Promise.all([
    getSession(),
    api.get<RegistrationApplicationResponse[]>(`/admin/registration-applications?status=${statusFilter}`),
    api.get<RegistrationApplicationResponse[]>("/admin/registration-applications"),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);
  const branchById = new Map(branches.map((b) => [b.id, b]));
  const canReview = session?.role === "ADMIN";
  const canSubmit = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";
  const canExport = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";
  const submittedCount = allApplications.filter((a) => a.status === "SUBMITTED").length;
  const approvedCount = allApplications.filter((a) => a.status === "APPROVED").length;
  const rejectedCount = allApplications.filter((a) => a.status === "REJECTED").length;

  // Export honors the chosen period regardless of which status tab is currently browsed —
  // the visible list stays tab-filtered, only the export additionally bounds by submittedAt.
  const fromInstant = new Date(`${from}T00:00:00Z`).getTime();
  const toInstant = new Date(`${to}T23:59:59Z`).getTime();
  const exportApplications = applications.filter((a) => {
    const submitted = new Date(a.submittedAt).getTime();
    return submitted >= fromInstant && submitted <= toInstant;
  });
  const exportRows: RegistrationExportRow[] = exportApplications.map((a) => ({
    name: `${a.firstName} ${a.lastName}`,
    role: ROLE_LABEL[a.targetRole] ?? a.targetRole,
    branch: branchById.get(a.branchId)?.name ?? a.branchId,
    status: dict.registrations.applicationStatus[a.status],
    submittedAt: new Date(a.submittedAt).toLocaleString(),
    reason: a.rejectionReason ?? "",
  }));

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title={dict.registrations.pageTitle} subtitle={dict.registrations.pageSubtitle} />

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 flex-1">
          <StatCard icon="edit-note" label={dict.registrations.applicationStatus.SUBMITTED} value={submittedCount.toLocaleString()} alert={submittedCount > 0} />
          <StatCard icon="check-circle" label={dict.registrations.applicationStatus.APPROVED} value={approvedCount.toLocaleString()} />
          <StatCard icon="warning" label={dict.registrations.applicationStatus.REJECTED} value={rejectedCount.toLocaleString()} />
        </div>
        {canSubmit && (
          <Link
            href="/registrations/new"
            className="inline-flex items-center justify-center gap-2 min-h-12 px-4 rounded-[var(--radius-md)] text-sm font-semibold bg-primary text-on-primary hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98] w-full sm:w-auto"
          >
            <Icon name="plus" className="size-5" />
            {dict.registrations.newApplication}
          </Link>
        )}
      </div>

      {canExport && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <DateRangeFilter status={statusFilter} from={from} to={to} />
          <RegistrationsExportButtons
            filenameBase={`microfi-registrations_${statusFilter}_${from}_${to}`}
            meta={{
              scope: session?.role === "ADMIN" ? dict.export.scopeAllBranches : (branches.find((b) => b.id === session?.branchId)?.name ?? dict.export.scopeAllBranches),
              from,
              to,
              generatedBy: session?.sub ?? "",
            }}
            rows={exportRows}
          />
        </div>
      )}

      <div className="flex gap-1 border-b-2 border-outline-variant">
        {(["SUBMITTED", "APPROVED", "REJECTED"] as const).map((s) => (
          <Link
            key={s}
            href={`/registrations?status=${s}&from=${from}&to=${to}`}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 -mb-0.5 transition-colors ${
              statusFilter === s ? "border-primary text-primary" : "border-transparent text-text-slate hover:text-primary"
            }`}
          >
            {dict.registrations.applicationStatus[s]}
          </Link>
        ))}
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="divide-y divide-outline-variant">
          {applications.map((application) => {
            const branch = branchById.get(application.branchId);
            const badgeStatus = STATUS_BADGE[application.status];
            const badgeLabel = dict.registrations.applicationStatus[application.status];
            return (
              <div key={application.id} className="card-interactive flex items-start gap-4 p-5">
                <Link href={`/registrations/${application.id}`} className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-4">
                    <span className="font-semibold text-on-surface hover:text-primary">
                      {application.firstName} {application.lastName}
                    </span>
                    <Badge status={badgeStatus} label={badgeLabel} />
                  </div>
                  <p className="text-sm text-text-slate mt-1">
                    {ROLE_LABEL[application.targetRole]} &middot; {branch ? `${branch.name} (${branch.code})` : application.branchId}
                  </p>
                  <p className="text-xs text-text-grey-disabled mt-1">
                    {t(dict.registrations.submittedAt, { date: new Date(application.submittedAt).toLocaleString() })}
                  </p>
                  {application.status === "REJECTED" && application.rejectionReason && (
                    <p className="text-xs text-danger-red mt-2">{t(dict.registrations.reasonPrefix, { reason: application.rejectionReason })}</p>
                  )}
                </Link>
                {application.status === "SUBMITTED" && canReview && (
                  <div className="flex items-center gap-2 shrink-0">
                    <ApproveButton applicationId={application.id} login={application.login} targetRole={application.targetRole} />
                    <RejectApplicationModal applicationId={application.id} />
                  </div>
                )}
              </div>
            );
          })}
        </div>
        {applications.length === 0 && (
          <div className="p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
            <Icon name="edit-note" className="size-6 text-outline-variant" />
            {t(dict.registrations.noApplications, { status: dict.registrations.applicationStatus[statusFilter].toLowerCase() })}
          </div>
        )}
      </div>
    </div>
  );
}

function StatCard({ icon, label, value, alert = false }: { icon: IconName; label: string; value: string; alert?: boolean }) {
  return (
    <div className={`bg-surface-container-lowest border-2 rounded-[var(--radius-md)] p-4 flex flex-col gap-3 ${alert ? "border-error/40" : "border-outline-variant"}`}>
      <div className={`h-9 w-9 rounded-[var(--radius-sm)] flex items-center justify-center ${alert ? "bg-error-container text-on-error-container" : "bg-primary-container/10 text-primary"}`}>
        <Icon name={icon} className="size-5" />
      </div>
      <div>
        <p className="text-xs text-on-surface-variant uppercase tracking-widest mb-1 font-semibold">{label}</p>
        <p className={`font-bold text-2xl tabular-nums ${alert ? "text-error" : "text-primary"}`}>{value}</p>
      </div>
    </div>
  );
}
