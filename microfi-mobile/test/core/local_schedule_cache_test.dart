import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/local_schedule_cache.dart';

import 'secure_storage_test_utils.dart';

void main() {
  final storage = MockSecureStorage();
  setUp(storage.install);
  tearDown(storage.uninstall);

  group('LocalScheduleCache', () {
    test('returns null when nothing has been saved yet', () async {
      expect(await LocalScheduleCache('agent-1').read(), isNull);
    });

    test('round-trips a saved schedule', () async {
      final cache = LocalScheduleCache('agent-1');
      await cache.save(openTime: '08:00:00', closeTime: '17:00:00');

      final snapshot = await cache.read();
      expect(snapshot, isNotNull);
      expect(snapshot!.openTime, '08:00:00');
      expect(snapshot.closeTime, '17:00:00');
    });

    test('round-trips a branch with no configured hours (both null)', () async {
      final cache = LocalScheduleCache('agent-1');
      await cache.save(openTime: null, closeTime: null);

      final snapshot = await cache.read();
      expect(snapshot!.openTime, isNull);
      expect(snapshot.closeTime, isNull);
      expect(snapshot.isWithin(DateTime(2026, 9, 10, 3, 0)), isTrue);
    });

    test('isWithin delegates to isWithinScheduleWindow correctly', () async {
      final cache = LocalScheduleCache('agent-1');
      await cache.save(openTime: '08:00:00', closeTime: '17:00:00');

      final snapshot = await cache.read();
      expect(snapshot!.isWithin(DateTime(2026, 9, 10, 12, 0)), isTrue);
      expect(snapshot.isWithin(DateTime(2026, 9, 10, 20, 0)), isFalse);
    });

    test('a later save overwrites the earlier snapshot', () async {
      final cache = LocalScheduleCache('agent-1');
      await cache.save(openTime: '08:00:00', closeTime: '17:00:00');
      await cache.save(openTime: '06:00:00', closeTime: '20:00:00');

      final snapshot = await cache.read();
      expect(snapshot!.openTime, '06:00:00');
      expect(snapshot.closeTime, '20:00:00');
    });

    test('keeps independent snapshots per agent id', () async {
      await LocalScheduleCache('agent-a').save(openTime: '08:00:00', closeTime: '17:00:00');
      await LocalScheduleCache('agent-b').save(openTime: '06:00:00', closeTime: '22:00:00');

      expect((await LocalScheduleCache('agent-a').read())!.openTime, '08:00:00');
      expect((await LocalScheduleCache('agent-b').read())!.openTime, '06:00:00');
    });

    test("clear() removes the snapshot without touching another agent's", () async {
      final agentA = LocalScheduleCache('agent-a');
      final agentB = LocalScheduleCache('agent-b');
      await agentA.save(openTime: '08:00:00', closeTime: '17:00:00');
      await agentB.save(openTime: '06:00:00', closeTime: '22:00:00');

      await agentA.clear();

      expect(await agentA.read(), isNull);
      expect((await agentB.read())!.openTime, '06:00:00');
    });
  });
}
