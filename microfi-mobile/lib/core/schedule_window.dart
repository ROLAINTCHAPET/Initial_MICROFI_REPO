/// Byte-for-byte the same algorithm as the server's AgentDirectoryService#requireWithinScheduleWindow
/// and LocationTrackingService's own _withinScheduleWindow — compares the device's local wall-clock
/// time against the branch's configured hours, treating the device's local time as the branch's own
/// timezone rather than doing full IANA timezone math. A deliberate simplification already made and
/// documented elsewhere in this app: it holds in practice, since a field agent's phone is physically
/// located in the branch's own timezone while they're out collecting for it.
bool isWithinScheduleWindow(String? openTime, String? closeTime, DateTime now) {
  if (openTime == null || closeTime == null) return true;

  final openMinutes = _parseMinutes(openTime);
  final closeMinutes = _parseMinutes(closeTime);
  if (openMinutes == null || closeMinutes == null) return true;

  final nowMinutes = now.hour * 60 + now.minute;
  return nowMinutes >= openMinutes && nowMinutes < closeMinutes;
}

int? _parseMinutes(String hhmmss) {
  final parts = hhmmss.split(':');
  if (parts.length < 2) return null;
  final hour = int.tryParse(parts[0]);
  final minute = int.tryParse(parts[1]);
  if (hour == null || minute == null) return null;
  return hour * 60 + minute;
}
