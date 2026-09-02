import { SuspendAgentButton } from "./SuspendAgentButton";
import { TopUpEscrowModal } from "./TopUpEscrowModal";
import { WaiverModal } from "./WaiverModal";
import { ResetDeviceBindingModal } from "./ResetDeviceBindingModal";
import { ResetPasswordModal } from "./ResetPasswordModal";
import { DeleteAgentModal } from "./DeleteAgentModal";
import { AgentGeofenceLink } from "./AgentGeofenceLink";
import type { AgentResponse, EscrowResponse } from "@/lib/types";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

// Two-column card grid, split into a neutral "management" section and a red-flagged "critical"
// section — matches Graphical Design/stitch_microfi_digital_cash_network. Each card is its own
// component's trigger (see ActionCard), so this file only decides layout/grouping, never styling.
export async function AgentAdministrationPanel({ agent, escrow }: { agent: AgentResponse; escrow: EscrowResponse | null }) {
  const dict = getDictionary(await getLocale());

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h3 className="text-h2 text-on-surface mb-4">{dict.agents.detail.managementActions}</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <TopUpEscrowModal agentId={agent.id} isPendingCeiling={agent.status === "PENDING_CEILING"} />
          <WaiverModal agentId={agent.id} currentCeiling={escrow?.effectiveCeilingXaf ?? 0} />
          <ResetPasswordModal agentId={agent.id} username={agent.username} />
          {agent.imei !== null && <ResetDeviceBindingModal agentId={agent.id} bound={agent.imei !== null} />}
          <AgentGeofenceLink agentId={agent.id} />
        </div>
      </div>

      <div>
        <h3 className="text-h2 text-error mb-4">{dict.agents.detail.criticalActions}</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <SuspendAgentButton agentId={agent.id} status={agent.status} hasCeiling={(escrow?.baseCeilingXaf ?? 0) > 0} />
          <DeleteAgentModal agentId={agent.id} />
        </div>
      </div>
    </div>
  );
}
