import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { EmptyState } from "@/components/Table";
import type { BranchResponse } from "@/lib/types";
import { NewRegistrationForm } from "./NewRegistrationForm";

// Same ADMIN/BRANCH_MANAGER-only gate as Settings — a BRANCH_CASHIER never creates accounts
// today either, so there's no reason to expose the submission form to that role.
export default async function NewRegistrationApplicationPage() {
  const session = await getSession();

  if (session?.role !== "ADMIN" && session?.role !== "BRANCH_MANAGER") {
    return <EmptyState>Submitting a registration application is only available to an Administrator or Branch Manager.</EmptyState>;
  }

  const branches = await api.get<BranchResponse[]>("/admin/branches");

  return <NewRegistrationForm branches={branches} callerRole={session.role} callerBranchId={session.branchId} />;
}
