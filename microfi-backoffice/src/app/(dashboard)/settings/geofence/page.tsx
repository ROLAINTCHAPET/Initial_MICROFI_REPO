import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { PageHeader } from "@/components/PageHeaderContext";
import { Icon } from "@/components/Icon";
import { EmptyState } from "@/components/Table";
import type { BranchResponse } from "@/lib/types";
import { BranchGeofenceBulkEditor } from "@/components/branches/BranchGeofenceBulkEditor";
import { BackLink } from "@/components/BackLink";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

export default async function BranchGeofenceBulkPage({
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
    <div className="max-w-6xl mx-auto w-full flex flex-col gap-4">
      <BackLink href={`/settings?branchId=${branch.id}`} label={dict.settings.backToSettings} />

      <div className="flex items-center text-xs text-on-surface-variant gap-2">
        <Link href={`/settings?branchId=${branch.id}`} className="hover:text-primary transition-colors">{dict.settings.pageTitle}</Link>
        <Icon name="chevron-right" className="size-4" />
        <span className="text-on-surface font-semibold">{dict.branches.geofenceBulk.sectionTitle}</span>
      </div>
      <PageHeader title={dict.branches.geofenceBulk.sectionTitle} subtitle={`${branch.name} (${branch.code})`} />
      <BranchGeofenceBulkEditor key={branch.id} branchId={branch.id} branchLabel={`${branch.name} (${branch.code})`} />
    </div>
  );
}
