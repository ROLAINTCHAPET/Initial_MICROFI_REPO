// Mirrors AgentDirectoryService#isBranchPastCloseTime exactly (same "no schedule configured means
// no restriction" fallback) so the UI's disabled state never disagrees with the backend's actual
// gate on POST /ofj/{branchId}/reconcile.
export function isPastBranchCloseTime(branch: { closeTime: string | null; timezone: string | null } | undefined): boolean {
  if (!branch?.closeTime || !branch?.timezone) return true;
  try {
    const parts = new Intl.DateTimeFormat("en-GB", {
      timeZone: branch.timezone,
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    }).formatToParts(new Date());
    const get = (type: string) => parts.find((p) => p.type === type)?.value ?? "00";
    const nowTime = `${get("hour")}:${get("minute")}:${get("second")}`;
    return nowTime >= branch.closeTime;
  } catch {
    // Unparseable/unknown timezone in the data — fail open rather than crash the page; the
    // backend will still enforce the real gate regardless of what the UI shows.
    return true;
  }
}
