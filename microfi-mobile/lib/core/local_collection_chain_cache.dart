import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'collection_chain_codec.dart';

/// What this installation believes is the last record in its own collection hash-chain — the
/// next collection's previousHash/counter are derived from this, both for an online submission
/// and for one merely queued offline (an offline-queued item still advances what the installation
/// believes it recorded; the server catches any divergence at sync time — see
/// CollectionService#applyChainRules on the backend).
class ChainState {
  final int lastCounter;
  final String lastHash;

  ChainState({required this.lastCounter, required this.lastHash});
}

/// Same save/read shape as LocalCeilingCache/LocalGeofenceCache/LocalScheduleCache, but
/// deliberately keyed by **installationId**, not agentId, and deliberately NOT part of the
/// sign-out clear-list those three are in: this chain is a property of the installation's
/// server-side binding, not of any one login session, so an ordinary sign-out/sign-in on the same
/// installation (no admin reset involved) must find it exactly as it left it — clearing it there
/// would desync the app's next counter from the one the server actually expects. The only time
/// this legitimately needs to reset is when a login response carries a brand-new
/// installationSecret (proof the server just created a fresh binding — see
/// AuthRepository#login/login_screen.dart) — that call site clears this explicitly.
class LocalCollectionChainCache {
  static const _secureStorage = FlutterSecureStorage();
  final String installationId;

  LocalCollectionChainCache(this.installationId);

  String get _key => 'collection_chain_$installationId';

  Future<void> save({required int lastCounter, required String lastHash}) => _secureStorage.write(
        key: _key,
        value: jsonEncode({'lastCounter': lastCounter, 'lastHash': lastHash}),
      );

  Future<ChainState?> read() async {
    final raw = await _secureStorage.read(key: _key);
    if (raw == null) return null;
    final json = jsonDecode(raw) as Map<String, dynamic>;
    return ChainState(lastCounter: json['lastCounter'] as int, lastHash: json['lastHash'] as String);
  }

  /// The next record's counter/previousHash — 1/GENESIS when nothing has been recorded yet by
  /// this installation (a fresh bind, or one that's never had #save called).
  Future<({int counter, String previousHash})> nextLink() async {
    final state = await read();
    if (state == null) return (counter: 1, previousHash: CollectionChainCodec.genesisHash);
    return (counter: state.lastCounter + 1, previousHash: state.lastHash);
  }

  /// Call only when login_screen.dart just received a brand-new installationSecret — see this
  /// class's own doc comment for why sign-out must NOT call this. Safe to clear: the next
  /// nextLink() call just starts fresh at counter 1, matching the server's brand-new binding.
  Future<void> clear() => _secureStorage.delete(key: _key);
}
