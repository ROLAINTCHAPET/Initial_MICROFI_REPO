import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/local_ceiling_cache.dart';

import 'secure_storage_test_utils.dart';

void main() {
  final storage = MockSecureStorage();
  setUp(storage.install);
  tearDown(storage.uninstall);

  group('LocalCeilingCache', () {
    test('returns null when nothing has been saved yet', () async {
      expect(await LocalCeilingCache('agent-1').read(), isNull);
    });

    test('round-trips a saved snapshot', () async {
      final cache = LocalCeilingCache('agent-1');
      await cache.save(effectiveCeilingXaf: 500000, cumulativeTodayXaf: 120000);

      final snapshot = await cache.read();
      expect(snapshot, isNotNull);
      expect(snapshot!.effectiveCeilingXaf, 500000);
      expect(snapshot.cumulativeTodayXaf, 120000);
    });

    test('a later save overwrites the earlier snapshot', () async {
      final cache = LocalCeilingCache('agent-1');
      await cache.save(effectiveCeilingXaf: 500000, cumulativeTodayXaf: 120000);
      await cache.save(effectiveCeilingXaf: 500000, cumulativeTodayXaf: 340000);

      expect((await cache.read())!.cumulativeTodayXaf, 340000);
    });

    test('keeps independent snapshots per agent id', () async {
      await LocalCeilingCache('agent-a').save(effectiveCeilingXaf: 100000, cumulativeTodayXaf: 0);
      await LocalCeilingCache('agent-b').save(effectiveCeilingXaf: 900000, cumulativeTodayXaf: 0);

      expect((await LocalCeilingCache('agent-a').read())!.effectiveCeilingXaf, 100000);
      expect((await LocalCeilingCache('agent-b').read())!.effectiveCeilingXaf, 900000);
    });

    test("clear() removes the snapshot without touching another agent's", () async {
      final agentA = LocalCeilingCache('agent-a');
      final agentB = LocalCeilingCache('agent-b');
      await agentA.save(effectiveCeilingXaf: 100000, cumulativeTodayXaf: 50000);
      await agentB.save(effectiveCeilingXaf: 900000, cumulativeTodayXaf: 10000);

      await agentA.clear();

      expect(await agentA.read(), isNull);
      expect((await agentB.read())!.effectiveCeilingXaf, 900000);
    });
  });

  group('CeilingSnapshot.cumulativeIsFromToday', () {
    test('true for a snapshot taken moments ago', () {
      final snapshot = CeilingSnapshot(effectiveCeilingXaf: 1, cumulativeTodayXaf: 1, asOfUtc: DateTime.now().toUtc());
      expect(snapshot.cumulativeIsFromToday, isTrue);
    });

    test('false for a snapshot from a previous UTC day', () {
      final snapshot = CeilingSnapshot(
        effectiveCeilingXaf: 1,
        cumulativeTodayXaf: 1,
        asOfUtc: DateTime.now().toUtc().subtract(const Duration(days: 1)),
      );
      expect(snapshot.cumulativeIsFromToday, isFalse);
    });
  });
}
