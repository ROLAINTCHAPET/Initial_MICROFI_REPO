import 'dart:async';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import '../features/home/branch_repository.dart';
import '../features/home/location_repository.dart';
import '../l10n/app_localizations.dart';

/// UC-10 — periodic GPS position reporting while the agent's session is open, which is what
/// actually feeds server-side geofence breach evaluation (UC-13/GeofenceService.evaluateLocation)
/// — without pings, an assigned geofence just sits there unused. Sampling cadence (~5 min,
/// NFR-09 battery optimisation) and the schedule-window stop (NFR-10) are deliberately the mobile
/// client's job, not the server's (see TrackingService's own doc comment) — this is that job.
///
/// Runs as a genuine Android foreground service (geolocator's built-in
/// ForegroundNotificationConfig), not a plain in-app Timer + one-shot fetch as this used to be.
/// Verified via logcat on a real Redmi/MIUI device that a bare Timer got killed by MIUI's
/// ProcessSceneCleaner ~13s after the app lost foreground focus — not "eventually while
/// backgrounded", almost immediately on any momentary focus loss (a call, a notification, the
/// screen locking) — silently ending tracking/geofence-alerting for the rest of the agent's day
/// with no indication to them. A foreground service earns elevated process priority that a plain
/// activity-bound Timer never gets. This is a mitigation, not an absolute guarantee — the plugin's
/// own docs are explicit that it "does not prevent Android from killing the activity", and MIUI's
/// battery manager in particular can still override it unless the agent has also granted this app
/// "No restrictions" under Settings > Battery > App battery saver (a device-provisioning step, not
/// something this app can force programmatically).
class LocationTrackingService {
  static const _interval = Duration(minutes: 5);

  final String token;
  final String agentId;
  final AppLocalizations l10n;

  StreamSubscription<Position>? _subscription;
  AgentBranch? _branch;

  LocationTrackingService({required this.token, required this.agentId, required this.l10n});

  Future<void> start() async {
    if (_subscription != null) return;
    await _refreshBranchSchedule();

    // Best-effort: on Android 13+ this gates whether the foreground-service notification is
    // actually shown, not whether the service itself can start — never worth blocking on.
    try {
      await [Permission.notification].request();
    } catch (_) {}

    if (!await Geolocator.isLocationServiceEnabled()) return;
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
      return;
    }

    _subscription = Geolocator.getPositionStream(
      locationSettings: AndroidSettings(
        accuracy: LocationAccuracy.medium,
        intervalDuration: _interval,
        distanceFilter: 0,
        foregroundNotificationConfig: ForegroundNotificationConfig(
          notificationTitle: l10n.ltsNotificationTitle,
          notificationText: l10n.ltsNotificationText,
          notificationChannelName: l10n.ltsNotificationChannelName,
          setOngoing: true,
        ),
      ),
    ).listen(_onPosition, onError: (_) {});
  }

  void stop() {
    _subscription?.cancel();
    _subscription = null;
  }

  Future<void> _refreshBranchSchedule() async {
    try {
      _branch = await BranchRepository(token).fetchMyBranch();
    } catch (_) {
      // Best-effort — an unknown schedule falls back to "always sample" (see _withinScheduleWindow).
    }
  }

  Future<void> _onPosition(Position position) async {
    if (!_withinScheduleWindow()) return;
    try {
      await LocationRepository(token).sendPing(agentId, position.latitude, position.longitude);
    } catch (_) {
      // Best-effort: a missed ping (offline, transient server error) is just one fewer trail
      // point — never worth surfacing to the agent mid-collection-round.
    }
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
