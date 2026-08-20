import 'dart:async';
import 'package:geolocator/geolocator.dart';
import '../features/home/branch_repository.dart';
import '../features/home/location_repository.dart';

/// UC-10 — periodic GPS position reporting while the agent's session is open, which is what
/// actually feeds server-side geofence breach evaluation (UC-13/GeofenceService.evaluateLocation)
/// — without pings, an assigned geofence just sits there unused. Sampling cadence (~5 min,
/// NFR-09 battery optimisation) and the schedule-window stop (NFR-10) are deliberately the mobile
/// client's job, not the server's (see TrackingService's own doc comment) — this is that job.
///
/// Foreground-only: ticks while this app instance is alive, same as the rest of this app's
/// networking. There's no native background-execution plumbing (WorkManager/BGTaskScheduler) in
/// this project yet, so pings stop once the app is fully backgrounded/killed by the OS, not just
/// when the screen locks — a real limitation, not a corner deliberately cut invisibly.
class LocationTrackingService {
  static const _interval = Duration(minutes: 5);

  final String token;
  final String agentId;

  Timer? _timer;
  AgentBranch? _branch;

  LocationTrackingService({required this.token, required this.agentId});

  void start() {
    if (_timer != null) return;
    _refreshBranchSchedule();
    unawaited(_tick());
    _timer = Timer.periodic(_interval, (_) => _tick());
  }

  void stop() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> _refreshBranchSchedule() async {
    try {
      _branch = await BranchRepository(token).fetchMyBranch();
    } catch (_) {
      // Best-effort — an unknown schedule falls back to "always sample" (see _withinScheduleWindow).
    }
  }

  Future<void> _tick() async {
    if (!_withinScheduleWindow()) return;
    try {
      final position = await _capturePassively();
      if (position == null) return;
      await LocationRepository(token).sendPing(agentId, position.latitude, position.longitude);
    } catch (_) {
      // Best-effort: a missed ping (no fix, offline, transient server error) is just one fewer
      // trail point — never worth surfacing to the agent mid-collection-round.
    }
  }

  /// Unlike core/location.dart's captureCurrentLocation (used for the mandatory collection GPS
  /// gate), this never *requests* permission — a passive background-ish ping popping a permission
  /// dialog out of nowhere every 5 minutes would be a genuinely bad experience. If permission was
  /// never granted, pings are simply skipped; the agent already sees why via the collection flow.
  Future<Position?> _capturePassively() async {
    if (!await Geolocator.isLocationServiceEnabled()) return null;
    final permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
      return null;
    }
    return Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.medium),
    );
  }

  /// Compares the device's local wall-clock time against the branch's configured hours. This
  /// treats the device's local time as the branch's own timezone rather than doing full IANA
  /// timezone math — a deliberate simplification (no timezone package dependency in this app yet)
  /// that holds in practice, since a field agent's phone is physically located in the branch's
  /// own timezone while they're out collecting for it.
  bool _withinScheduleWindow() {
    final branch = _branch;
    final open = branch?.openTime;
    final close = branch?.closeTime;
    if (open == null || close == null) return true;

    final openMinutes = _parseMinutes(open);
    final closeMinutes = _parseMinutes(close);
    if (openMinutes == null || closeMinutes == null) return true;

    final now = DateTime.now();
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
}
