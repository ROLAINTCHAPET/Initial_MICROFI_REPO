import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { EmptyState } from "@/components/Table";
import type { BranchResponse } from "@/lib/types";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";
import { AuditExplorer } from "./AuditExplorer";

// Security & Administrative Trail — same sensitivity boundary the app already applies to
// Settings/Registrations/Tracking: ADMIN sees the whole network, BRANCH_MANAGER only their own
// branch, BRANCH_CASHIER has no access at all (their export surface is OFJ reconciliation only).
export default async function AuditLogPage() {
  const session = await getSession();
  const dict = getDictionary(await getLocale());

  if (session?.role !== "ADMIN" && session?.role !== "BRANCH_MANAGER") {
    return <EmptyState>{dict.audit.accessDenied}</EmptyState>;
  }

  const branches = session.role === "ADMIN" ? await api.get<BranchResponse[]>("/admin/branches") : [];

  return (
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title={dict.audit.pageTitle} subtitle={dict.audit.pageSubtitle} />
      <AuditExplorer
        role={session.role}
        ownBranchId={session.branchId}
        branches={branches}
        generatedBy={session.sub}
      />
    </div>
  );
}
