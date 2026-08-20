import Link from "next/link";
import { api } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { Badge } from "@/components/Badge";
import { Icon, type IconName } from "@/components/Icon";
import type { AdminRole, AdminUserResponse, BranchResponse } from "@/lib/types";
import { AdminUserStatusButton } from "./AdminUserStatusButton";
import { ResetPasswordModal } from "./ResetPasswordModal";

const ROLE_META: Record<AdminRole, { label: string; icon: IconName; chipClass: string }> = {
  ADMIN: { label: "Administrator", icon: "shield-check", chipClass: "bg-primary-container text-white" },
  BRANCH_MANAGER: { label: "Branch Manager", icon: "agents", chipClass: "bg-secondary-fixed text-on-secondary-fixed-variant" },
  BRANCH_CASHIER: { label: "Branch Cashier", icon: "account-balance-wallet", chipClass: "bg-tertiary-fixed text-on-tertiary-fixed-variant" },
};

export default async function TeamMemberDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [session, user] = await Promise.all([getSession(), api.get<AdminUserResponse>(`/admin/users/${id}`)]);
  const branch = user.branchId ? await api.get<BranchResponse>(`/admin/branches/${user.branchId}/schedule`).catch(() => null) : null;

  const canManage = session?.role === "ADMIN" || (session?.role === "BRANCH_MANAGER" && user.branchId === session?.branchId);
  const meta = ROLE_META[user.role];

  return (
    <div className="max-w-3xl mx-auto w-full">
      <div className="mb-6">
        <div className="flex items-center text-xs text-on-surface-variant gap-2 mb-2">
          <Link href="/team" className="hover:text-primary transition-colors">
            Team
          </Link>
          <Icon name="chevron-right" className="size-4" />
          <span className="text-on-surface font-semibold">{user.login}</span>
        </div>

        <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-display text-primary">{user.login}</h1>
              <Badge status={user.status} />
            </div>
            <p className="text-sm text-on-surface-variant mt-1">{meta.label}</p>
          </div>
          {canManage && (
            <div className="flex gap-3">
              <ResetPasswordModal userId={user.id} login={user.login} />
              <AdminUserStatusButton userId={user.id} status={user.status} />
            </div>
          )}
        </div>
      </div>

      <div className="bg-surface-container-lowest border-2 border-outline-variant rounded-xl p-6">
        <div className="flex items-center gap-4 mb-6 pb-6 border-b-2 border-outline-variant">
          <div className="w-16 h-16 rounded-full bg-primary-container/10 border-2 border-primary-container/20 flex items-center justify-center text-primary font-bold text-2xl shrink-0">
            {user.login.slice(0, 2).toUpperCase()}
          </div>
          <div>
            <span className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-[var(--radius-full)] text-xs font-bold ${meta.chipClass}`}>
              <Icon name={meta.icon} className="size-4" />
              {meta.label}
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          <InfoField label="Login" value={user.login} mono />
          <InfoField label="Status" value={user.status === "ACTIVE" ? "Active" : "Suspended"} />
          <InfoField label="Branch" value={branch?.name ?? (user.role === "ADMIN" ? "All branches (global)" : "—")} />
          <InfoField label="Account ID" value={user.id} mono />
        </div>
      </div>
    </div>
  );
}

function InfoField({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <div className="text-xs text-on-surface-variant">{label}</div>
      <div className={`text-sm text-primary ${mono ? "font-mono" : ""}`}>{value}</div>
    </div>
  );
}
