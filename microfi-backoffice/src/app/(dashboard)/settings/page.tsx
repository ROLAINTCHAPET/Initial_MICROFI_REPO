import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { EmptyState } from "@/components/Table";
import { Icon } from "@/components/Icon";
import { BranchSettingsForm } from "@/components/branches/BranchSettingsForm";
import { SettingsBranchSelector } from "./SettingsBranchSelector";
import type { BranchResponse } from "@/lib/types";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

// Same screen ADMIN and BRANCH_MANAGER both use to configure a branch (Hours/Contact/Cashier
// Cap/IMEI requirement) — everything BRANCH_MANAGER can set here, ADMIN can too, for any branch,
// mirroring the Team page's ADMIN-picks-a-branch pattern. BRANCH_CASHIER has no settings access.
export default async function BranchSettingsPage({
  searchParams,
}: {
  searchParams: Promise<{ branchId?: string }>;
}) {
  const session = await getSession();
  const dict = getDictionary(await getLocale());

  if (session?.role !== "ADMIN" && session?.role !== "BRANCH_MANAGER") {
    return <EmptyState>{dict.settings.accessDenied}</EmptyState>;
  }

  const branches = await api.get<BranchResponse[]>("/admin/branches");
  const params = await searchParams;
  const branchId = session.role === "ADMIN" ? (params.branchId ?? branches[0]?.id) : session.branchId;
  const branch = branches.find((b) => b.id === branchId);

  if (!branch) {
    return <EmptyState>{session.role === "ADMIN" ? dict.settings.noBranchesYet : dict.settings.branchNotFound}</EmptyState>;
  }

  return (
    <div className="max-w-xl mx-auto w-full flex flex-col gap-6">
      <PageHeader title={dict.settings.pageTitle} subtitle={`${branch.name} (${branch.code})`} />

      {session.role === "ADMIN" && (
        <div className="flex items-center gap-3 flex-wrap w-full sm:w-auto">
          <span className="flex items-center gap-2 text-sm font-semibold text-on-surface-variant shrink-0">
            <Icon name="location-on" className="size-5 text-primary" />
            {dict.settings.viewingBranch}
          </span>
          <SettingsBranchSelector branches={branches} selectedBranchId={branch.id} />
        </div>
      )}

      <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-[var(--radius-md)] p-6">
        <BranchSettingsForm
          key={branch.id}
          branchId={branch.id}
          openTime={branch.openTime}
          closeTime={branch.closeTime}
          openTimeLocked={branch.openTimeLocked}
          phone={branch.phone}
          maxCashiers={branch.maxCashiers}
          requireImei={branch.requireImei}
          defaultCeilingPct={branch.defaultCeilingPct}
        />
      </div>
    </div>
  );
}
