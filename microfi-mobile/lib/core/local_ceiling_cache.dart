import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// The agent's ceiling and cumulative-today total, as of the last time the server actually
/// confirmed them. Always a snapshot, never a live value — [asOfUtc] says how stale it is.
class CeilingSnapshot {
  final int effectiveCeilingXaf;
  final int cumulativeTodayXaf;
  final DateTime asOfUtc;

  CeilingSnapshot({required this.effectiveCeilingXaf, required this.cumulativeTodayXaf, required this.asOfUtc});

  /// The ceiling itself changes rarely (an admin decision), but cumulativeTodayXaf resets at UTC
  /// midnight server-side (see CollectionService#enforceEscrowCeiling) — once the snapshot is from
  /// a previous day, its cumulative figure no longer means anything and must not be trusted.
  bool get cumulativeIsFromToday {
    final now = DateTime.now().toUtc();
    return asOfUtc.year == now.year && asOfUtc.month == now.month && asOfUtc.day == now.day;
  }
}

/// A best-effort local mirror of GET /agents/{id}/escrow, refreshed opportunistically every time
/// that call actually succeeds (Home load, the collection wizard's live preview). Its only job is
/// letting an offline collection be warned about — or blocked from — exceeding the ceiling before
/// the client is handed a receipt, instead of only finding out at sync. The server remains the
/// sole authority: it re-checks BR-03 on every sync item regardless of what this cache says.
class LocalCeilingCache {
  static const _secureStorage = FlutterSecureStorage();
  final String agentId;

  LocalCeilingCache(this.agentId);

  String get _key => 'ceiling_snapshot_$agentId';

  Future<void> save({required int effectiveCeilingXaf, required int cumulativeTodayXaf}) => _secureStorage.write(
        key: _key,
        value: jsonEncode({
          'effectiveCeilingXaf': effectiveCeilingXaf,
          'cumulativeTodayXaf': cumulativeTodayXaf,
          'asOfUtc': DateTime.now().toUtc().toIso8601String(),
        }),
      );

  Future<CeilingSnapshot?> read() async {
    final raw = await _secureStorage.read(key: _key);
    if (raw == null) return null;
    final json = jsonDecode(raw) as Map<String, dynamic>;
    return CeilingSnapshot(
      effectiveCeilingXaf: json['effectiveCeilingXaf'] as int,
      cumulativeTodayXaf: json['cumulativeTodayXaf'] as int,
      asOfUtc: DateTime.parse(json['asOfUtc'] as String),
    );
  }

  /// Call on sign-out — a stale snapshot for an agent no longer using this device has no reason
  /// to keep existing. Safe to clear freely: the next read() just returns null, the same "no
  /// cache yet" state a fresh install starts in.
  Future<void> clear() => _secureStorage.delete(key: _key);
}
