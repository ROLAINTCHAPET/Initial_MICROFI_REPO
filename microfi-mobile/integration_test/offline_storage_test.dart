// Exercises the pieces that can't be verified in a headless sandbox: real sqflite_sqlcipher (the
// offline collection queue) and real Keystore/Keychain-backed secure storage (LocalPinVerifier,
// LocalCeilingCache) on an actual device/emulator. `flutter test test/` already covers the pure
// logic with a mocked secure-storage channel — this file is specifically the "does it actually
// work against the real platform" check that sandbox can't answer.
//
// Run with a device/emulator attached:
//   flutter test integration_test/offline_storage_test.dart -d <device-id>
//
// Each test uses a fresh, randomly-suffixed agent id so repeated runs on the same physical device
// don't collide with leftover data from a previous run.
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

import 'package:microfi_mobile/core/local_ceiling_cache.dart';
import 'package:microfi_mobile/core/local_pin_verifier.dart';
import 'package:microfi_mobile/features/collection/collection_repository.dart';
import 'package:microfi_mobile/features/collection/offline_queue_repository.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  String freshAgentId() => 'it-${const Uuid().v4()}';

  PendingCollection sampleCollection({
    required String deviceTxId,
    required String clientId,
    int amountXaf = 15000,
    String? collectedAtIso,
    String pin = '4821',
    String terminalId = 'it-terminal-1',
  }) =>
      PendingCollection(
        deviceTxId: deviceTxId,
        clientId: clientId,
        clientName: 'Test Client',
        amountXaf: amountXaf,
        lat: 4.0511,
        lon: 9.7679,
        accuracyM: 12.5,
        collectedAtIso: collectedAtIso ?? DateTime.now().toUtc().toIso8601String(),
        denominationLines: [DenominationLine(faceValueXaf: 5000, quantity: amountXaf ~/ 5000)],
        pin: pin,
        terminalId: terminalId,
      );

  group('OfflineQueueRepository (real SQLCipher)', () {
    testWidgets('add/list/removeByDeviceTxIds round-trips a collection, including its PIN', (tester) async {
      final agentId = freshAgentId();
      final repo = OfflineQueueRepository(agentId);
      final deviceTxId = const Uuid().v4();

      await repo.add(sampleCollection(deviceTxId: deviceTxId, clientId: 'client-1', pin: '7734'));
      final pending = await repo.list();

      expect(pending.length, 1);
      expect(pending.single.deviceTxId, deviceTxId);
      expect(pending.single.clientId, 'client-1');
      expect(pending.single.pin, '7734');

      await repo.removeByDeviceTxIds({deviceTxId});
      expect(await repo.list(), isEmpty);
    });

    testWidgets('adding the same deviceTxId twice replaces rather than duplicates', (tester) async {
      final agentId = freshAgentId();
      final repo = OfflineQueueRepository(agentId);
      final deviceTxId = const Uuid().v4();

      await repo.add(sampleCollection(deviceTxId: deviceTxId, clientId: 'client-1', amountXaf: 10000));
      await repo.add(sampleCollection(deviceTxId: deviceTxId, clientId: 'client-1', amountXaf: 25000));

      final pending = await repo.list();
      expect(pending.length, 1);
      expect(pending.single.amountXaf, 25000);

      await repo.removeByDeviceTxIds({deviceTxId});
    });

    testWidgets('keeps each agent\'s queue isolated from every other agent\'s', (tester) async {
      final agentA = freshAgentId();
      final agentB = freshAgentId();
      final txA = const Uuid().v4();
      final txB = const Uuid().v4();

      await OfflineQueueRepository(agentA).add(sampleCollection(deviceTxId: txA, clientId: 'client-a'));
      await OfflineQueueRepository(agentB).add(sampleCollection(deviceTxId: txB, clientId: 'client-b'));

      expect((await OfflineQueueRepository(agentA).list()).map((c) => c.deviceTxId), [txA]);
      expect((await OfflineQueueRepository(agentB).list()).map((c) => c.deviceTxId), [txB]);

      await OfflineQueueRepository(agentA).removeByDeviceTxIds({txA});
      await OfflineQueueRepository(agentB).removeByDeviceTxIds({txB});
    });

    testWidgets('imports a pre-SQLite legacy blob once, then leaves the legacy key gone', (tester) async {
      final agentId = freshAgentId();
      const legacyDeviceTxId = 'legacy-tx-1';
      final legacyJson = '''
      [
        {
          "deviceTxId": "$legacyDeviceTxId",
          "clientId": "legacy-client",
          "clientName": "Legacy Client",
          "amountXaf": 8000,
          "lat": 4.05,
          "lon": 9.75,
          "accuracyM": 15.0,
          "collectedAt": "${DateTime.now().toUtc().toIso8601String()}",
          "denominationLines": [{"faceValueXaf": 2000, "quantity": 4}]
        }
      ]
      ''';
      const secureStorage = FlutterSecureStorage();
      final legacyKey = 'offline_collections_$agentId';
      await secureStorage.write(key: legacyKey, value: legacyJson);

      final pending = await OfflineQueueRepository(agentId).list();

      expect(pending.length, 1);
      expect(pending.single.deviceTxId, legacyDeviceTxId);
      // Pre-migration rows predate per-item PIN capture — expect the documented empty fallback.
      expect(pending.single.pin, '');
      expect(await secureStorage.read(key: legacyKey), isNull, reason: 'legacy key must be deleted once imported');

      await OfflineQueueRepository(agentId).removeByDeviceTxIds({legacyDeviceTxId});
    });
  });

  group('LocalPinVerifier (real Keystore/Keychain)', () {
    testWidgets('seeds and verifies a PIN against real secure storage', (tester) async {
      final agentId = freshAgentId();
      final verifier = LocalPinVerifier(agentId);

      expect(await verifier.verify('4821'), isTrue, reason: 'no seed yet — must fall back to accept, not hang or throw');
      await verifier.seed('4821');
      expect(await verifier.verify('4821'), isTrue);
      expect(await verifier.verify('0000'), isFalse);
    });

    testWidgets('locks out after repeated wrong attempts on real storage', (tester) async {
      final agentId = freshAgentId();
      final verifier = LocalPinVerifier(agentId);
      await verifier.seed('4821');

      for (var i = 0; i < 5; i++) {
        await verifier.verify('0000');
      }
      expect(await verifier.lockoutRemaining(), isNotNull);
    });
  });

  group('LocalCeilingCache (real Keystore/Keychain)', () {
    testWidgets('saves and reads back a snapshot against real secure storage', (tester) async {
      final agentId = freshAgentId();
      final cache = LocalCeilingCache(agentId);

      expect(await cache.read(), isNull);
      await cache.save(effectiveCeilingXaf: 500000, cumulativeTodayXaf: 75000);

      final snapshot = await cache.read();
      expect(snapshot!.effectiveCeilingXaf, 500000);
      expect(snapshot.cumulativeTodayXaf, 75000);
      expect(snapshot.cumulativeIsFromToday, isTrue);
    });
  });

  // Not asserted (no oracle for "is this actually encrypted" from Dart alone), but printed so a
  // human reviewing a real-device test run can eyeball that SQLCipher is genuinely active — a
  // plaintext SQLite file would show readable field values in this dump; an encrypted one won't.
  group('sanity', () {
    testWidgets('offline queue db file is not human-readable plaintext', (tester) async {
      final agentId = freshAgentId();
      final deviceTxId = const Uuid().v4();
      await OfflineQueueRepository(agentId).add(sampleCollection(deviceTxId: deviceTxId, clientId: 'plaintext-check-client'));

      // Same filename convention as OfflineQueueRepository._open() — the file is shared across
      // agents (rows are scoped by the agent_id column), so no per-agent path is needed here.
      final dir = await getApplicationDocumentsDirectory();
      final dbFile = File(p.join(dir.path, 'microfi_offline.db'));
      final raw = await dbFile.readAsBytes();
      final asLatin1 = String.fromCharCodes(raw.where((b) => b < 256));
      debugPrint('offline queue db at ${dbFile.path} — contains literal clientId "plaintext-check-client": ${asLatin1.contains('plaintext-check-client')}');
      expect(asLatin1.contains('plaintext-check-client'), isFalse);

      await OfflineQueueRepository(agentId).removeByDeviceTxIds({deviceTxId});
    });
  });
}
