import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { EmptyState } from "@/components/Table";
import { BranchesWorkspace } from "@/components/branches/BranchesWorkspace";
import { BranchDirectory, type BranchRow } from "@/components/branches/BranchDirectory";
import { CreateBranchModal } from "@/components/branches/CreateBranchModal";
import type { BranchResponse, ScheduleDefaultsResponse } from "@/lib/types";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

// ADMIN sees and can edit every branch, plus the org-wide schedule defaults and branch creation
// (same widgets previously buried at the bottom of the home dashboard — relocated here so they
// have their own sidebar entry instead of living inside "Dashboard"). BRANCH_MANAGER gets the same
// directory view of every branch (useful context beyond their own), but BranchDirectory's existing
// canEdit gate (see editRestrictedTooltip) limits actual edits to their own branch — no schedule
// defaults or branch creation, both genuinely org-wide/ADMIN-only actions.
export default async function BranchesPage() {
  const session = await getSession();
  const dict = getDictionary(await getLocale());

  if (session?.role !== "ADMIN" && session?.role !== "BRANCH_MANAGER") {
    return <EmptyState>{dict.branches.accessDenied}</EmptyState>;
  }
  const isAdmin = session.role === "ADMIN";

  const [branches, scheduleDefaults] = await Promise.all([
    api.get<BranchResponse[]>("/admin/branches"),
    api.get<ScheduleDefaultsResponse>("/admin/branches/schedule-defaults"),
  ]);

  const branchRows: BranchRow[] = branches.map((b) => ({
    id: b.id,
    code: b.code,
    name: b.name,
    phone: b.phone,
    timezone: b.timezone,
    openTime: b.openTime,
    closeTime: b.closeTime,
    openTimeLocked: b.openTimeLocked,
    maxCashiers: b.maxCashiers,
    requireImei: b.requireImei,
    defaultCeilingPct: b.defaultCeilingPct,
    requireClientActivation: b.requireClientActivation,
    canEdit: isAdmin || b.id === session.branchId,
  }));

  return (
    <div className="max-w-7xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title={dict.branches.pageTitle} subtitle={dict.branches.pageSubtitle} />
      {isAdmin ? (
        <BranchesWorkspace
          scheduleDefaults={scheduleDefaults}
          editable
          branches={branchRows}
          actions={<CreateBranchModal />}
          generatedBy={session.sub ?? ""}
        />
      ) : (
        <BranchDirectory branches={branchRows} generatedBy={session.sub ?? ""} />
      )}
    </div>
  );
}
