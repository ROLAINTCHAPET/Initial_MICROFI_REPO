import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'schedule_window.dart';

/// The branch's assigned open/close hours, as of the last time the server actually confirmed
/// them. Always a snapshot, never a live value — a manager can change a branch's schedule at any
/// time, so this can go stale exactly like CeilingSnapshot/GeofenceSnapshot can.
class ScheduleSnapshot {
  final String? openTime;
  final String? closeTime;

  ScheduleSnapshot({required this.openTime, required this.closeTime});

  bool isWithin(DateTime now) => isWithinScheduleWindow(openTime, closeTime, now);
}

/// A best-effort local mirror of GET /agents/me/branch's openTime/closeTime, refreshed
/// opportunistically every time that call actually succeeds (Home load, the collection wizard's
/// live preview) — mirrors LocalCeilingCache/LocalGeofenceCache exactly, just for the branch's
/// schedule window instead of the ceiling or the geofence. Its only job is letting an offline
/// collection outside business hours be blocked before the client is handed a receipt, instead of
/// only finding out at sync. The server remains the sole authority: it re-checks the schedule
/// window on every sync item regardless of what this cache says.
class LocalScheduleCache {
  static const _secureStorage = FlutterSecureStorage();
  final String agentId;

  LocalScheduleCache(this.agentId);

  String get _key => 'schedule_snapshot_$agentId';

  Future<void> save({required String? openTime, required String? closeTime}) => _secureStorage.write(
        key: _key,
        value: jsonEncode({'openTime': openTime, 'closeTime': closeTime}),
      );

  Future<ScheduleSnapshot?> read() async {
    final raw = await _secureStorage.read(key: _key);
    if (raw == null) return null;
    final json = jsonDecode(raw) as Map<String, dynamic>;
    return ScheduleSnapshot(openTime: json['openTime'] as String?, closeTime: json['closeTime'] as String?);
  }

  /// Call on sign-out — a stale snapshot for an agent no longer using this device has no reason
  /// to keep existing. Safe to clear freely: the next read() just returns null, the same "no
  /// cache yet" state a fresh install starts in.
  Future<void> clear() => _secureStorage.delete(key: _key);
}
