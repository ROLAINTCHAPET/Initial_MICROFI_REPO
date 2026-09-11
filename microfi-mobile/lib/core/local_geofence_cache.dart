import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../features/home/agent_profile.dart';

/// The agent's assigned geofence, as of the last time the server actually confirmed it. Always a
/// snapshot, never a live value — an agent's geofence can be reassigned by a manager at any time,
/// so this can go stale exactly like [CeilingSnapshot] can, just without a "which day" freshness
/// concept: a geofence doesn't reset daily, it just occasionally changes.
class GeofenceSnapshot {
  final List<GeofenceVertex> vertices;

  GeofenceSnapshot({required this.vertices});

  /// Mirrors GeofenceService#isWithinAssignedGeofence's "unassigned = unrestricted" default.
  bool get isUnrestricted => vertices.isEmpty;
}

/// A best-effort local mirror of GET /agents/me/geofence, refreshed opportunistically every time
/// that call actually succeeds (Home load, the collection wizard's live preview) — mirrors
/// [LocalCeilingCache] exactly, just for the geofence polygon instead of the ceiling. Its only job
/// is letting an offline collection outside the assigned zone be blocked before the client is
/// handed a receipt, instead of only finding out at sync. The server remains the sole authority:
/// it re-checks the geofence on every sync item regardless of what this cache says.
class LocalGeofenceCache {
  static const _secureStorage = FlutterSecureStorage();
  final String agentId;

  LocalGeofenceCache(this.agentId);

  String get _key => 'geofence_snapshot_$agentId';

  Future<void> save(AgentGeofence geofence) => _secureStorage.write(
        key: _key,
        value: jsonEncode({
          'vertices': geofence.vertices.map((v) => {'lat': v.lat, 'lon': v.lon}).toList(),
        }),
      );

  Future<GeofenceSnapshot?> read() async {
    final raw = await _secureStorage.read(key: _key);
    if (raw == null) return null;
    final json = jsonDecode(raw) as Map<String, dynamic>;
    final vertices = (json['vertices'] as List<dynamic>)
        .map((v) => GeofenceVertex(lat: (v['lat'] as num).toDouble(), lon: (v['lon'] as num).toDouble()))
        .toList();
    return GeofenceSnapshot(vertices: vertices);
  }

  /// Call on sign-out — a stale snapshot for an agent no longer using this device has no reason
  /// to keep existing. Safe to clear freely: the next read() just returns null, the same "no
  /// cache yet" state a fresh install starts in.
  Future<void> clear() => _secureStorage.delete(key: _key);
}
