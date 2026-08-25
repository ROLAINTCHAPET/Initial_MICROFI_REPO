import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Badge, type BadgeStatus } from "@/components/Badge";
import { Icon } from "@/components/Icon";
import type { BranchResponse, RegistrationApplicationResponse, RegistrationApplicationStatus } from "@/lib/types";
import { ApproveButton } from "../ApproveButton";
import { RejectApplicationModal } from "../RejectApplicationModal";
import { getDictionary, type Dictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

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

function documentList(dict: Dictionary): { type: string; label: string }[] {
  return [
    { type: "NATIONAL_ID", label: dict.registrations.documents.nationalId },
    { type: "CRIMINAL_RECORD", label: dict.registrations.documents.criminalRecord },
    { type: "MEDICAL_FITNESS", label: dict.registrations.documents.medicalFitness },
    { type: "LOCATION_PLAN", label: dict.registrations.documents.locationPlan },
    { type: "PASSPORT_PHOTO", label: dict.registrations.documents.passportPhoto },
  ];
}

function Field({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div>
      <p className="text-xs text-on-surface-variant uppercase tracking-widest font-semibold">{label}</p>
      <p className="text-sm text-on-surface">{value || "—"}</p>
    </div>
  );
}

export default async function RegistrationApplicationDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const dict = getDictionary(await getLocale());
  const ROLE_LABEL = roleLabel(dict);
  const DOCUMENTS = documentList(dict);
  const { id } = await params;
  const [session, application, branches] = await Promise.all([
    getSession(),
    api.get<RegistrationApplicationResponse>(`/admin/registration-applications/${id}`),
    api.get<BranchResponse[]>("/admin/branches"),
  ]);
  const branch = branches.find((b) => b.id === application.branchId);
  const badgeStatus = STATUS_BADGE[application.status];
  const badgeLabel = dict.registrations.applicationStatus[application.status];
  const canReview = session?.role === "ADMIN";

  return (
    <div className="max-w-3xl mx-auto w-full flex flex-col gap-6">
      <Link href="/registrations" className="text-sm text-primary hover:underline underline-offset-2 font-medium flex items-center gap-1 w-fit">
        <Icon name="arrow-upward" className="size-4 -rotate-90" />
        {dict.registrations.backToRegistrations}
      </Link>
      <PageHeader
        title={`${application.firstName} ${application.lastName}`}
        subtitle={`${ROLE_LABEL[application.targetRole]} · ${branch ? `${branch.name} (${branch.code})` : application.branchId}`}
      />

      <div className="flex items-center justify-between">
        <Badge status={badgeStatus} label={badgeLabel} />
        {application.status === "SUBMITTED" && canReview && (
          <div className="flex items-center gap-2">
            <ApproveButton applicationId={application.id} login={application.login} targetRole={application.targetRole} />
            <RejectApplicationModal applicationId={application.id} />
          </div>
        )}
      </div>

      {application.status === "REJECTED" && (
        <div className="p-4 rounded-[var(--radius-sm)] border-2 border-danger-red/40 bg-error-container/10">
          <p className="text-sm text-on-surface"><strong>{dict.registrations.detail.rejectionReasonLabel}</strong> {application.rejectionReason}</p>
        </div>
      )}

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant p-5 grid grid-cols-2 gap-4">
        <Field label={dict.registrations.fields.firstName} value={application.firstName} />
        <Field label={dict.registrations.fields.lastName} value={application.lastName} />
        <Field label={dict.registrations.fields.dateOfBirth} value={application.dateOfBirth} />
        <Field label={dict.registrations.fields.phone} value={application.phone} />
        <Field label={dict.registrations.fields.username} value={application.login} />
        <Field label={dict.registrations.fields.email} value={application.email} />
        <Field label={dict.registrations.fields.employeeCode} value={application.employeeCode} />
        <Field label={dict.registrations.fields.nationalIdNumber} value={application.nationalIdNumber} />
        <Field label={dict.registrations.fields.taxIdNumber} value={application.taxIdNumber} />
        <Field label={dict.registrations.fields.placeOfResidence} value={application.placeOfResidence} />
        <Field label={dict.registrations.detail.criminalRecordIssued} value={application.criminalRecordIssuedDate} />
        <Field label={dict.registrations.fields.submitted} value={new Date(application.submittedAt).toLocaleString()} />
        {application.reviewedAt && <Field label={dict.registrations.detail.reviewed} value={new Date(application.reviewedAt).toLocaleString()} />}
        {application.activationSmsStatus && <Field label={dict.registrations.detail.activationSms} value={application.activationSmsStatus} />}
      </div>

      <div className="bg-surface-container-lowest rounded-[var(--radius-md)] border-2 border-outline-variant overflow-hidden">
        <div className="flex items-center gap-2 px-5 py-4 border-b-2 border-outline-variant bg-surface-bright font-bold text-on-surface">
          <Icon name="edit-note" className="size-5 text-primary" />
          {dict.registrations.detail.submittedDocumentsHeader}
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
