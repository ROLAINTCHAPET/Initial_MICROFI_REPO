import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/collection_chain_codec.dart';
import 'package:microfi_mobile/core/local_collection_chain_cache.dart';

import 'secure_storage_test_utils.dart';

void main() {
  final storage = MockSecureStorage();
  setUp(storage.install);
  tearDown(storage.uninstall);

  group('LocalCollectionChainCache', () {
    test('nextLink starts at counter 1 with GENESIS previousHash when nothing has been saved', () async {
      final next = await LocalCollectionChainCache('install-1').nextLink();
      expect(next.counter, 1);
      expect(next.previousHash, CollectionChainCodec.genesisHash);
    });

    test('round-trips a saved chain state', () async {
      final cache = LocalCollectionChainCache('install-1');
      await cache.save(lastCounter: 3, lastHash: 'hash-3');

      final state = await cache.read();
      expect(state, isNotNull);
      expect(state!.lastCounter, 3);
      expect(state.lastHash, 'hash-3');
    });

    test('nextLink derives the next counter/previousHash from the last saved state', () async {
      final cache = LocalCollectionChainCache('install-1');
      await cache.save(lastCounter: 3, lastHash: 'hash-3');

      final next = await cache.nextLink();
      expect(next.counter, 4);
      expect(next.previousHash, 'hash-3');
    });

    test('a later save overwrites the earlier state', () async {
      final cache = LocalCollectionChainCache('install-1');
      await cache.save(lastCounter: 1, lastHash: 'hash-1');
      await cache.save(lastCounter: 2, lastHash: 'hash-2');

      expect((await cache.read())!.lastCounter, 2);
    });

    test('keeps independent chains per agent id', () async {
      await LocalCollectionChainCache('install-a').save(lastCounter: 5, lastHash: 'hash-a');
      await LocalCollectionChainCache('install-b').save(lastCounter: 9, lastHash: 'hash-b');

      expect((await LocalCollectionChainCache('install-a').read())!.lastCounter, 5);
      expect((await LocalCollectionChainCache('install-b').read())!.lastCounter, 9);
    });

    test("clear() removes the state without touching another agent's", () async {
      final agentA = LocalCollectionChainCache('install-a');
      final agentB = LocalCollectionChainCache('install-b');
      await agentA.save(lastCounter: 1, lastHash: 'hash-a');
      await agentB.save(lastCounter: 1, lastHash: 'hash-b');

      await agentA.clear();

      expect(await agentA.read(), isNull);
      expect((await agentB.read())!.lastHash, 'hash-b');
    });

    test('clear() resets nextLink back to counter 1 with GENESIS previousHash', () async {
      final cache = LocalCollectionChainCache('install-1');
      await cache.save(lastCounter: 7, lastHash: 'hash-7');
      await cache.clear();

      final next = await cache.nextLink();
      expect(next.counter, 1);
      expect(next.previousHash, CollectionChainCodec.genesisHash);
    });
  });
}
