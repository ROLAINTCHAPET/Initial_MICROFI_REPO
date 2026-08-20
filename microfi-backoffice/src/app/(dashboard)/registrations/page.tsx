import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Badge, type BadgeStatus } from "@/components/Badge";
import { Icon, type IconName } from "@/components/Icon";
import type { BranchResponse, RegistrationApplicationResponse, RegistrationApplicationStatus } from "@/lib/types";
import { ApproveButton } from "./ApproveButton";
import { RejectApplicationModal } from "./RejectApplicationModal";

const STATUS_BADGE: Record<RegistrationApplicationStatus, { status: BadgeStatus; label: string }> = {
  SUBMITTED: { status: "PENDING", label: "Pending Review" },
  APPROVED: { status: "ACTIVE", label: "Approved" },
  REJECTED: { status: "SUSPENDED", label: "Rejected" },
};

const ROLE_LABEL: Record<string, string> = {
  AGENT: "Field Agent",
  BRANCH_MANAGER: "Branch Manager",
  BRANCH_CASHIER: "Branch Cashier",
};

export default async function RegistrationsPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string }>;
}) {
  const params = await searchParams;
  const statusFilter = params.status === "APPROVED" || params.status === "REJECTED" ? params.status : "SUBMITTED";

  const [session, applications, allApplications, branches] = await Promise.all([
    getSession(),
    api.get<RegistrationApplicationResponse[]>(`/admin/registration-applications?status=${statusFilter}`),
    api.get<RegistrationApplicationResponse[]>("/admin/registration-applications"),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);
  const branchById = new Map(branches.map((b) => [b.id, b]));
  const canReview = session?.role === "ADMIN";
  const canSubmit = session?.role === "ADMIN" || session?.role === "BRANCH_MANAGER";
  const submittedCount = allApplications.filter((a) => a.status === "SUBMITTED").length;
  const approvedCount = allApplications.filter((a) => a.status === "APPROVED").length;
  const rejectedCount = allApplications.filter((a) => a.status === "REJECTED").length;

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title="Registrations" subtitle="Compliance-gated digital enrollment dossiers for agents, cashiers, and branch managers." />

      <div className="flex items-center justify-between flex-wrap gap-4">
        <div className="grid grid-cols-3 gap-4 flex-1">
          <StatCard icon="edit-note" label="Pending Review" value={submittedCount.toLocaleString()} alert={submittedCount > 0} />
          <StatCard icon="check-circle" label="Approved" value={approvedCount.toLocaleString()} />
          <StatCard icon="warning" label="Rejected" value={rejectedCount.toLocaleString()} />
        </div>
        {canSubmit && (
          <Link
            href="/registrations/new"
            className="inline-flex items-center justify-center gap-2 min-h-12 px-4 rounded-[var(--radius-md)] text-sm font-semibold bg-primary text-on-primary hover:bg-primary/90 transition-[background-color,transform] duration-150 ease-out hover:scale-[1.03] active:scale-[0.98]"
          >
            <Icon name="plus" className="size-5" />
            New Application
          </Link>
        )}
      </div>

      <div className="flex gap-1 border-b-2 border-outline-variant">
        {(["SUBMITTED", "APPROVED", "REJECTED"] as const).map((s) => (
          <Link
            key={s}
            href={`/registrations?status=${s}`}
            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-semibold border-b-2 -mb-0.5 transition-colors ${
              statusFilter === s ? "border-primary text-primary" : "border-transparent text-text-slate hover:text-primary"
            }`}
          >
            {STATUS_BADGE[s].label}
          </Link>
        ))}
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="divide-y divide-outline-variant">
          {applications.map((application) => {
            const branch = branchById.get(application.branchId);
            const badge = STATUS_BADGE[application.status];
            return (
              <div key={application.id} className="card-interactive flex items-start gap-4 p-5">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-4">
                    <Link href={`/registrations/${application.id}`} className="font-semibold text-on-surface hover:text-primary">
                      {application.firstName} {application.lastName}
                    </Link>
                    <Badge status={badge.status} label={badge.label} />
                  </div>
                  <p className="text-sm text-text-slate mt-1">
                    {ROLE_LABEL[application.targetRole]} &middot; {branch ? `${branch.name} (${branch.code})` : application.branchId}
                  </p>
                  <p className="text-xs text-text-grey-disabled mt-1">Submitted {new Date(application.submittedAt).toLocaleString()}</p>
                  {application.status === "SUBMITTED" && canReview && (
                    <div className="mt-3 flex items-center gap-2">
                      <ApproveButton applicationId={application.id} login={application.login} targetRole={application.targetRole} />
                      <RejectApplicationModal applicationId={application.id} />
                    </div>
                  )}
                  {application.status === "REJECTED" && application.rejectionReason && (
                    <p className="text-xs text-danger-red mt-2">Reason: {application.rejectionReason}</p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
        {applications.length === 0 && (
          <div className="p-10 flex flex-col items-center gap-2 text-center text-sm text-text-slate">
            <Icon name="edit-note" className="size-6 text-outline-variant" />
            No {STATUS_BADGE[statusFilter].label.toLowerCase()} applications.
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
