import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { EmptyState } from "@/components/Table";
import type { BranchResponse } from "@/lib/types";
import { NewClientForm } from "./NewClientForm";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

// Same ADMIN/BRANCH_MANAGER-only gate as POST /admin/clients itself — a cashier can look clients
// up but never seed new ones.
export default async function NewClientPage() {
  const dict = getDictionary(await getLocale());
  const session = await getSession();

  if (session?.role !== "ADMIN" && session?.role !== "BRANCH_MANAGER") {
    return <EmptyState>{dict.clients.createModal.restrictedMessage}</EmptyState>;
  }

  const branches = await api.get<BranchResponse[]>("/admin/branches");

  return <NewClientForm branches={branches} callerRole={session.role} callerBranchId={session.branchId} />;
}
