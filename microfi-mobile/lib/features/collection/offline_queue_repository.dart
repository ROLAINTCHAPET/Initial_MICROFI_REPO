import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'collection_repository.dart';

/// A collection recorded while offline (FR-07) — persisted locally until "Sync Now" (or the next
/// successful submit) uploads it via POST /collections/sync, the same batch endpoint the backend
/// already exposes for this exact purpose.
class PendingCollection {
  final String deviceTxId;
  final String clientId;
  final String? clientName;
  final int amountXaf;
  final double lat;
  final double lon;
  final double? accuracyM;
  final String collectedAtIso;
  final List<DenominationLine> denominationLines;

  PendingCollection({
    required this.deviceTxId,
    required this.clientId,
    required this.clientName,
    required this.amountXaf,
    required this.lat,
    required this.lon,
    required this.accuracyM,
    required this.collectedAtIso,
    required this.denominationLines,
  });

  Map<String, dynamic> toRequestBody() => {
        'clientId': clientId,
        'amountXaf': amountXaf,
        'lat': lat,
        'lon': lon,
        if (accuracyM != null) 'accuracyM': accuracyM,
        'collectedAt': collectedAtIso,
        'deviceTxId': deviceTxId,
        'denominationLines': denominationLines.map((d) => d.toJson()).toList(),
      };

  Map<String, dynamic> toStorageJson() => {
        ...toRequestBody(),
        'clientName': clientName,
      };

  factory PendingCollection.fromStorageJson(Map<String, dynamic> json) => PendingCollection(
        deviceTxId: json['deviceTxId'] as String,
        clientId: json['clientId'] as String,
        clientName: json['clientName'] as String?,
        amountXaf: (json['amountXaf'] as num).toInt(),
        lat: (json['lat'] as num).toDouble(),
        lon: (json['lon'] as num).toDouble(),
        accuracyM: (json['accuracyM'] as num?)?.toDouble(),
        collectedAtIso: json['collectedAt'] as String,
        denominationLines: (json['denominationLines'] as List<dynamic>)
            .map((e) => DenominationLine(faceValueXaf: (e['faceValueXaf'] as num).toInt(), quantity: (e['quantity'] as num).toInt()))
            .toList(),
      );
}

/// Persists the local sync queue per agent — FR-07's own postcondition requires this stay
/// encrypted on device (client identity, amount, GPS fix, and denomination breakdown all sit
/// here until synced), so this uses the same Keystore/Keychain-backed secure storage as the
/// session token (SessionStorage), not plain shared_preferences. flutter_secure_storage only
/// stores single string values per key — not lists — so the whole queue is kept as one
/// JSON-encoded array under a single key rather than one shared_preferences entry per item.
class OfflineQueueRepository {
  final String agentId;
  final _storage = const FlutterSecureStorage();

  OfflineQueueRepository(this.agentId);

  String get _key => 'offline_collections_$agentId';

  Future<List<PendingCollection>> list() async {
    final raw = await _storage.read(key: _key);
    if (raw == null || raw.isEmpty) return [];
    final decoded = jsonDecode(raw) as List<dynamic>;
    return decoded.map((e) => PendingCollection.fromStorageJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> add(PendingCollection collection) async {
    final current = await list();
    current.add(collection);
    await _writeAll(current);
  }

  Future<void> removeByDeviceTxIds(Set<String> deviceTxIds) async {
    final current = await list();
    final remaining = current.where((c) => !deviceTxIds.contains(c.deviceTxId)).toList();
    await _writeAll(remaining);
  }

  Future<void> _writeAll(List<PendingCollection> items) async {
    await _storage.write(key: _key, value: jsonEncode(items.map((c) => c.toStorageJson()).toList()));
  }
}
