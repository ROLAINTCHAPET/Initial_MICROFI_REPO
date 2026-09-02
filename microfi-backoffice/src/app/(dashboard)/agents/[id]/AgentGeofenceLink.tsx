import Link from "next/link";
import { Icon } from "@/components/Icon";
import { getDictionary } from "@/lib/i18n/dictionaries";
import { getLocale } from "@/lib/i18n/locale";

// Same visual as ActionCard (see components/ActionCard.tsx) but a real navigation link, not a
// button that opens a modal — geofence editing needs a full page for the map (see
// agents/[id]/geofence/page.tsx), it doesn't fit in a popup.
export async function AgentGeofenceLink({ agentId }: { agentId: string }) {
  const dict = getDictionary(await getLocale());
  return (
    <Link
      href={`/agents/${agentId}/geofence`}
      className="flex items-start gap-4 p-5 rounded-[var(--radius-md)] border-2 bg-surface-container-lowest border-outline-variant hover:border-primary hover:shadow-[var(--shadow-elevation-1)] transition-all group text-left"
    >
      <div className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 transition-colors bg-surface-container-high text-on-surface group-hover:bg-primary group-hover:text-on-primary">
        <Icon name="location-on" className="size-5" />
      </div>
      <div className="flex-1 min-w-0">
        <h4 className="font-semibold text-base mb-1 text-on-surface group-hover:text-primary transition-colors">{dict.tracking.workspace.configureGeofence}</h4>
        <p className="text-xs text-on-surface-variant">{dict.tracking.workspace.configureGeofenceDescription}</p>
      </div>
    </Link>
  );
}
