import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Badge, type BadgeStatus } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import type { BranchResponse, RegistrationApplicationResponse, RegistrationApplicationStatus } from "@/lib/types";
import { ApproveButton } from "../ApproveButton";
import { RejectApplicationModal } from "../RejectApplicationModal";

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

const DOCUMENTS: { type: string; label: string }[] = [
  { type: "NATIONAL_ID", label: "National ID / Passport scan" },
  { type: "CRIMINAL_RECORD", label: "Criminal record (casier judiciaire)" },
  { type: "MEDICAL_FITNESS", label: "Medical fitness certificate" },
  { type: "LOCATION_PLAN", label: "Location / home plan sketch" },
  { type: "PASSPORT_PHOTO", label: "Passport photo" },
];

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <p className="text-xs text-on-surface-variant uppercase tracking-widest font-semibold">{label}</p>
      <p className="text-sm text-on-surface">{value || "—"}</p>
    </div>
  );
}

export default async function RegistrationApplicationDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [session, application, branches] = await Promise.all([
    getSession(),
    api.get<RegistrationApplicationResponse>(`/admin/registration-applications/${id}`),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);
  const branch = branches.find((b) => b.id === application.branchId);
  const badge = STATUS_BADGE[application.status];
  const canReview = session?.role === "ADMIN";

  return (
    <div className="max-w-3xl mx-auto w-full flex flex-col gap-6">
      <Link href="/registrations" className="text-sm text-primary hover:underline underline-offset-2 font-medium flex items-center gap-1 w-fit">
        <Icon name="arrow-upward" className="size-4 -rotate-90" />
        Back to Registrations
      </Link>
      <PageHeader
        title={`${application.firstName} ${application.lastName}`}
        subtitle={`${ROLE_LABEL[application.targetRole]} · ${branch ? `${branch.name} (${branch.code})` : application.branchId}`}
      />

      <div className="flex items-center justify-between">
        <Badge status={badge.status} label={badge.label} />
        {application.status === "SUBMITTED" && canReview && (
          <div className="flex items-center gap-2">
            <ApproveButton applicationId={application.id} login={application.login} targetRole={application.targetRole} />
            <RejectApplicationModal applicationId={application.id} />
          </div>
        )}
      </div>

      {application.status === "REJECTED" && (
        <div className="p-4 rounded-[var(--radius-sm)] border-2 border-danger-red/40 bg-error-container/10">
          <p className="text-sm text-on-surface"><strong>Rejection reason:</strong> {application.rejectionReason}</p>
        </div>
      )}

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant p-5 grid grid-cols-2 gap-4">
        <Field label="First Name" value={application.firstName} />
        <Field label="Last Name" value={application.lastName} />
        <Field label="Phone" value={application.phone} />
        <Field label="Username" value={application.login} />
        <Field label="Email" value={application.email} />
        <Field label="Employee Code" value={application.employeeCode} />
        <Field label="National ID Number" value={application.nationalIdNumber} />
        <Field label="Tax ID Number" value={application.taxIdNumber} />
        <Field label="Place of Residence" value={application.placeOfResidence} />
        <Field label="Criminal Record Issued" value={application.criminalRecordIssuedDate} />
        <Field label="Submitted" value={new Date(application.submittedAt).toLocaleString()} />
        {application.reviewedAt && <Field label="Reviewed" value={new Date(application.reviewedAt).toLocaleString()} />}
        {application.activationSmsStatus && <Field label="Activation SMS" value={application.activationSmsStatus} />}
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="flex items-center gap-2 px-5 py-4 border-b-2 border-outline-variant bg-surface-bright font-bold text-on-surface">
          <Icon name="edit-note" className="size-5 text-primary" />
          Submitted Documents
        </div>
        <div className="divide-y divide-outline-variant">
          {DOCUMENTS.map((doc) => (
            <a
              key={doc.type}
              href={`/api/registration-applications/${application.id}/documents/${doc.type}`}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-between px-5 py-3 text-sm text-on-surface hover:bg-surface-container-low transition-colors"
            >
              {doc.label}
              <Icon name="chevron-right" className="size-4 text-on-surface-variant" />
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}
