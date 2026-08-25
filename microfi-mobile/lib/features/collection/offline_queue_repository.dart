import 'dart:convert';
import 'dart:math';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite_sqlcipher/sqflite.dart';

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
  /// Captured once, at the moment of collection — the same PIN entry an online collection already
  /// requires. Kept only inside this encrypted queue and never shown again, so sync can upload the
  /// batch the instant connectivity allows without a second prompt: the cash is already in hand and
  /// the receipt already given by the time sync runs, so re-confirming intent then protects nothing
  /// this PIN didn't already cover.
  final String pin;

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
    required this.pin,
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
        'pin': pin,
      };

  /// Only still used to decode rows left over from the pre-SQLite storage format during the
  /// one-time migration in [OfflineQueueRepository._migrateLegacyIfNeeded]. Those rows predate
  /// per-item PIN capture, so there's no real PIN to recover for them — empty forces one
  /// last manual sync confirmation for whatever was already queued before the upgrade.
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
        pin: json['pin'] as String? ?? '',
      );
}

/// Persists the local sync queue in a SQLCipher-encrypted SQLite database — matching the
/// architecture doc's own "SQLCipher / Hive" spec, rather than the flat encrypted-blob approach
/// this used before (one JSON array serialized under a single flutter_secure_storage key,
/// rewritten whole on every read/write). The database's own encryption passphrase is a random
/// value generated once and kept in that same Keystore/Keychain-backed secure storage as the
/// session token — secure storage now holds only that one small secret; the actual queued cash
/// data (client identity, amount, GPS fix, denomination breakdown) lives in the encrypted
/// database itself.
///
/// Note this only changes *how* the queue is stored, not whether it survives an app uninstall —
/// both the old blob and this database sit in the same app-private sandbox either way, and are
/// wiped identically when the app is deleted. That's a separate, unsolved problem (see the
/// branch-wide EOD block a stuck agent.pendingSyncCount can cause).
class OfflineQueueRepository {
  final String agentId;

  static const _legacyKeyPrefix = 'offline_collections_';
  static const _dbPasswordKey = 'offline_db_key';
  static const _secureStorage = FlutterSecureStorage();

  static Future<Database>? _dbFuture;

  OfflineQueueRepository(this.agentId);

  Future<Database> _db() => _dbFuture ??= _open();

  Future<Database> _open() async {
    final password = await _databasePassword();
    final dir = await getApplicationDocumentsDirectory();
    final path = p.join(dir.path, 'microfi_offline.db');
    return openDatabase(
      path,
      password: password,
      version: 2,
      onCreate: (db, version) => db.execute('''
        CREATE TABLE pending_collections (
          agent_id TEXT NOT NULL,
          device_tx_id TEXT NOT NULL,
          client_id TEXT NOT NULL,
          client_name TEXT,
          amount_xaf INTEGER NOT NULL,
          lat REAL NOT NULL,
          lon REAL NOT NULL,
          accuracy_m REAL,
          collected_at_iso TEXT NOT NULL,
          denomination_lines_json TEXT NOT NULL,
          pin TEXT NOT NULL DEFAULT '',
          PRIMARY KEY (agent_id, device_tx_id)
        )
      '''),
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 2) {
          await db.execute("ALTER TABLE pending_collections ADD COLUMN pin TEXT NOT NULL DEFAULT ''");
        }
      },
    );
  }

  Future<String> _databasePassword() async {
    final existing = await _secureStorage.read(key: _dbPasswordKey);
    if (existing != null && existing.isNotEmpty) return existing;
    final random = Random.secure();
    final generated = List<int>.generate(32, (_) => random.nextInt(256)).map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    await _secureStorage.write(key: _dbPasswordKey, value: generated);
    return generated;
  }

  /// One-time import of whatever this agent already had queued under the pre-SQLite storage key
  /// — so upgrading the app can never silently drop cash a collection already recorded offline.
  /// Idempotent: the legacy key is deleted once imported, so this is a no-op on every later call.
  Future<void> _migrateLegacyIfNeeded(Database db) async {
    final legacyKey = '$_legacyKeyPrefix$agentId';
    final raw = await _secureStorage.read(key: legacyKey);
    if (raw == null || raw.isEmpty) return;
    final legacyItems = (jsonDecode(raw) as List<dynamic>)
        .map((e) => PendingCollection.fromStorageJson(e as Map<String, dynamic>))
        .toList();
    final batch = db.batch();
    for (final item in legacyItems) {
      batch.insert('pending_collections', _toRow(item), conflictAlgorithm: ConflictAlgorithm.replace);
    }
    await batch.commit(noResult: true);
    await _secureStorage.delete(key: legacyKey);
  }

  Map<String, Object?> _toRow(PendingCollection c) => {
        'agent_id': agentId,
        'device_tx_id': c.deviceTxId,
        'client_id': c.clientId,
        'client_name': c.clientName,
        'amount_xaf': c.amountXaf,
        'lat': c.lat,
        'lon': c.lon,
        'accuracy_m': c.accuracyM,
        'collected_at_iso': c.collectedAtIso,
        'denomination_lines_json': jsonEncode(c.denominationLines.map((d) => d.toJson()).toList()),
        'pin': c.pin,
      };

  PendingCollection _fromRow(Map<String, Object?> row) => PendingCollection(
        deviceTxId: row['device_tx_id'] as String,
        clientId: row['client_id'] as String,
        clientName: row['client_name'] as String?,
        amountXaf: row['amount_xaf'] as int,
        lat: row['lat'] as double,
        lon: row['lon'] as double,
        accuracyM: row['accuracy_m'] as double?,
        collectedAtIso: row['collected_at_iso'] as String,
        denominationLines: (jsonDecode(row['denomination_lines_json'] as String) as List<dynamic>)
            .map((e) => DenominationLine(faceValueXaf: (e['faceValueXaf'] as num).toInt(), quantity: (e['quantity'] as num).toInt()))
            .toList(),
        pin: row['pin'] as String? ?? '',
      );

  Future<List<PendingCollection>> list() async {
    final db = await _db();
    await _migrateLegacyIfNeeded(db);
    final rows = await db.query('pending_collections', where: 'agent_id = ?', whereArgs: [agentId]);
    return rows.map(_fromRow).toList();
  }

  Future<void> add(PendingCollection collection) async {
    final db = await _db();
    await _migrateLegacyIfNeeded(db);
    await db.insert('pending_collections', _toRow(collection), conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<void> removeByDeviceTxIds(Set<String> deviceTxIds) async {
    if (deviceTxIds.isEmpty) return;
    final db = await _db();
    final placeholders = List.filled(deviceTxIds.length, '?').join(',');
    await db.delete(
      'pending_collections',
      where: 'agent_id = ? AND device_tx_id IN ($placeholders)',
      whereArgs: [agentId, ...deviceTxIds],
    );
  }
}
