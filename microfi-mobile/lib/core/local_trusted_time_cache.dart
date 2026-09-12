import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Offline Field Collection Security Algorithm v1.1 §11 — "the local implementation must not rely
/// only on a user-changeable wall clock." True OS monotonic time isn't readily available across
/// app restarts in Flutter without native platform code, so this takes the pragmatic version the
/// spec itself allows for (flag, don't hard-block, since the server remains the real enforcement
/// boundary): remember this device's own wall-clock reading every time the server round-trip
/// succeeds, and flag if a later reading is ever earlier than one already recorded since that
/// anchor — i.e. "has this device's clock gone backward since we last talked to the server,"
/// which needs no monotonic clock, just two of this app's own timestamps to compare.
class LocalTrustedTimeCache {
  static const _secureStorage = FlutterSecureStorage();
  final String agentId;

  LocalTrustedTimeCache(this.agentId);

  String get _key => 'trusted_time_anchor_$agentId';

  /// Call every time a server round-trip actually succeeds (same refresh points as
  /// LocalCeilingCache) — records "as of right now, the device clock read this."
  Future<void> recordSuccessfulServerContact() =>
      _secureStorage.write(key: _key, value: jsonEncode({'deviceWallClockUtc': DateTime.now().toUtc().toIso8601String()}));

  /// True if the device's current wall-clock reading is earlier than the last one recorded at a
  /// successful server contact — a sign the clock has been wound backward since then. False (not
  /// flagged) when there's no anchor yet, or the clock has only moved forward, which is normal.
  Future<bool> hasClockRolledBackSinceLastServerContact() async {
    final raw = await _secureStorage.read(key: _key);
    if (raw == null) return false;
    final json = jsonDecode(raw) as Map<String, dynamic>;
    final lastKnown = DateTime.parse(json['deviceWallClockUtc'] as String);
    return DateTime.now().toUtc().isBefore(lastKnown);
  }

  /// Call on sign-out — same reasoning as every other local cache: a stale anchor for an agent no
  /// longer using this device has no reason to keep existing.
  Future<void> clear() => _secureStorage.delete(key: _key);
}
