import { ResetPasswordModal } from "./ResetPasswordModal";
import { AdminUserStatusButton } from "./AdminUserStatusButton";
import { DeleteAdminUserModal } from "./DeleteAdminUserModal";
import type { AdminUserResponse } from "@/lib/types";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

// Same two-section card grid as AgentAdministrationPanel — management actions neutral, critical
// (destructive) actions red-flagged. Each card is its own component's trigger (see ActionCard).
export async function AdminUserAdministrationPanel({ user, canDelete }: { user: AdminUserResponse; canDelete: boolean }) {
  const dict = getDictionary(await getLocale());

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h3 className="text-h2 text-on-surface mb-4">{dict.agents.detail.managementActions}</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <ResetPasswordModal userId={user.id} login={user.login} />
        </div>
      </div>

      <div>
        <h3 className="text-h2 text-error mb-4">{dict.agents.detail.criticalActions}</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <AdminUserStatusButton userId={user.id} status={user.status} />
          {canDelete && <DeleteAdminUserModal userId={user.id} />}
        </div>
      </div>
    </div>
  );
}
