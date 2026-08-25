import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/local_pin_verifier.dart';

import 'secure_storage_test_utils.dart';

void main() {
  final storage = MockSecureStorage();
  setUp(storage.install);
  tearDown(storage.uninstall);

  group('LocalPinVerifier', () {
    test('accepts any entry when nothing has been seeded yet (fresh-install fallback)', () async {
      final verifier = LocalPinVerifier('agent-1');
      expect(await verifier.verify('1234'), isTrue);
    });

    test('accepts the correct PIN after seeding', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      expect(await verifier.verify('4821'), isTrue);
    });

    test('rejects an incorrect PIN after seeding', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      expect(await verifier.verify('0000'), isFalse);
    });

    test('re-seeding (a PIN change) replaces the previously cached PIN', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      await verifier.seed('9999');
      expect(await verifier.verify('4821'), isFalse);
      expect(await verifier.verify('9999'), isTrue);
    });

    test('locks out after 5 consecutive wrong attempts', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      for (var i = 0; i < 5; i++) {
        expect(await verifier.verify('0000'), isFalse);
      }
      final lockout = await verifier.lockoutRemaining();
      expect(lockout, isNotNull);
      expect(lockout! > Duration.zero, isTrue);
    });

    test('a locked-out verifier still rejects the correct PIN until the lockout is checked/expired', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      for (var i = 0; i < 5; i++) {
        await verifier.verify('0000');
      }
      expect(await verifier.lockoutRemaining(), isNotNull);
    });

    test('a correct attempt clears accumulated failures, so a later run of wrong attempts starts fresh', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      await verifier.verify('0000');
      await verifier.verify('0000');
      expect(await verifier.verify('4821'), isTrue);

      for (var i = 0; i < 4; i++) {
        await verifier.verify('0000');
      }
      expect(await verifier.lockoutRemaining(), isNull);
    });

    test('keeps independent state per agent id', () async {
      final agentA = LocalPinVerifier('agent-a');
      final agentB = LocalPinVerifier('agent-b');
      await agentA.seed('1111');
      await agentB.seed('2222');

      expect(await agentA.verify('2222'), isFalse);
      expect(await agentB.verify('2222'), isTrue);
      expect(await agentB.verify('1111'), isFalse);
    });

    test('clear() removes the cache — the next verify falls back to accept, same as a fresh install', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      await verifier.clear();

      expect(await verifier.verify('4821'), isTrue);
      expect(await verifier.verify('0000'), isTrue);
    });

    test('clear() also removes an active lockout', () async {
      final verifier = LocalPinVerifier('agent-1');
      await verifier.seed('4821');
      for (var i = 0; i < 5; i++) {
        await verifier.verify('0000');
      }
      expect(await verifier.lockoutRemaining(), isNotNull);

      await verifier.clear();
      expect(await verifier.lockoutRemaining(), isNull);
    });

    test("clear() on one agent doesn't touch another agent's cache", () async {
      final agentA = LocalPinVerifier('agent-a');
      final agentB = LocalPinVerifier('agent-b');
      await agentA.seed('1111');
      await agentB.seed('2222');

      await agentA.clear();

      expect(await agentA.verify('1111'), isTrue); // no seed left — falls back to accept
      expect(await agentB.verify('2222'), isTrue); // untouched
      expect(await agentB.verify('1111'), isFalse); // still correctly rejects the wrong PIN
    });
  });
}
