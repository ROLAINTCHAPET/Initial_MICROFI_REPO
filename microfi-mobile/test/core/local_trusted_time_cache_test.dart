import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/local_trusted_time_cache.dart';

import 'secure_storage_test_utils.dart';

void main() {
  final storage = MockSecureStorage();
  setUp(storage.install);
  tearDown(storage.uninstall);

  group('LocalTrustedTimeCache', () {
    test('does not flag a rollback when no anchor has ever been recorded', () async {
      final cache = LocalTrustedTimeCache('agent-1');
      expect(await cache.hasClockRolledBackSinceLastServerContact(), isFalse);
    });

    test('does not flag when the clock has only moved forward since the anchor', () async {
      final cache = LocalTrustedTimeCache('agent-1');
      await cache.recordSuccessfulServerContact();

      expect(await cache.hasClockRolledBackSinceLastServerContact(), isFalse);
    });

    test('flags a fabricated backward jump relative to the recorded anchor', () async {
      final cache = LocalTrustedTimeCache('agent-1');
      // Simulate an anchor recorded "in the future" relative to now, the same observable effect
      // a genuine backward clock change would produce without needing to mutate the system clock.
      const secureStorage = FlutterSecureStorage();
      await secureStorage.write(
        key: 'trusted_time_anchor_agent-1',
        value: '{"deviceWallClockUtc":"${DateTime.now().toUtc().add(const Duration(days: 1)).toIso8601String()}"}',
      );

      expect(await cache.hasClockRolledBackSinceLastServerContact(), isTrue);
    });

    test("clear() removes the anchor without touching another agent's", () async {
      final agentA = LocalTrustedTimeCache('agent-a');
      final agentB = LocalTrustedTimeCache('agent-b');
      await agentA.recordSuccessfulServerContact();
      await agentB.recordSuccessfulServerContact();

      await agentA.clear();

      expect(await agentA.hasClockRolledBackSinceLastServerContact(), isFalse);
      expect(await agentB.hasClockRolledBackSinceLastServerContact(), isFalse);
    });
  });
}
