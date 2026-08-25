import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Lets an offline collection catch a mistyped transaction PIN immediately, before the client is
/// ever handed a receipt — the alternative (only finding out at sync, minutes or hours later, by
/// which point the cash is collected and the receipt already given) is a real operational mess.
///
/// This never talks to the server and the server's own PIN hash is never sent to the device —
/// instead the app learns what a correct PIN looks like the moment it last saw one confirmed
/// online (a fresh collection recorded online, or a PIN set/changed), salts and stretches it, and
/// keeps only that in Keystore/Keychain-backed secure storage. A numeric 4-10 digit PIN is a small
/// keyspace, so this alone doesn't make an extracted hash safe against unlimited offline guessing
/// — the stretching plus the lockout below just raise that cost on top of the storage's own
/// encryption; a device compromise is still the real line of defense.
class LocalPinVerifier {
  static const _secureStorage = FlutterSecureStorage();
  static const _stretchRounds = 20000;
  static const _lockoutThreshold = 5;
  static const _lockoutDuration = Duration(minutes: 5);

  final String agentId;

  LocalPinVerifier(this.agentId);

  String get _hashKey => 'local_pin_hash_$agentId';
  String get _saltKey => 'local_pin_salt_$agentId';
  String get _failuresKey => 'local_pin_failures_$agentId';
  String get _lockedUntilKey => 'local_pin_locked_until_$agentId';

  /// Call right after any server-confirmed use of the real PIN — an online collection that just
  /// succeeded, or a successful PATCH /agents/me/pin — so the cached hash never drifts from what
  /// the server actually has on file.
  Future<void> seed(String pin) async {
    final salt = _randomSaltHex();
    await _secureStorage.write(key: _saltKey, value: salt);
    await _secureStorage.write(key: _hashKey, value: _stretch(pin, salt));
    await _secureStorage.delete(key: _failuresKey);
    await _secureStorage.delete(key: _lockedUntilKey);
  }

  /// Call on sign-out — this agent's cached hash has no reason to keep existing on a device they
  /// may no longer be the one using (a shared/reassigned phone, an off-boarded agent). Safe to
  /// clear freely: the next collection this agent makes offline, anywhere, just falls back to the
  /// same "no seed yet" behavior as a fresh install (accepted locally, verified for real at sync).
  Future<void> clear() async {
    await _secureStorage.delete(key: _hashKey);
    await _secureStorage.delete(key: _saltKey);
    await _secureStorage.delete(key: _failuresKey);
    await _secureStorage.delete(key: _lockedUntilKey);
  }

  /// Remaining lockout after too many wrong offline attempts, or null if not (or no longer) locked.
  Future<Duration?> lockoutRemaining() async {
    final raw = await _secureStorage.read(key: _lockedUntilKey);
    if (raw == null) return null;
    final until = DateTime.tryParse(raw);
    if (until == null) return null;
    final remaining = until.difference(DateTime.now());
    if (!remaining.isNegative) return remaining;
    await _secureStorage.delete(key: _lockedUntilKey);
    await _secureStorage.delete(key: _failuresKey);
    return null;
  }

  /// True if [pin] matches the cached hash. If nothing has been cached yet (a fresh install that
  /// hasn't had a single server-confirmed PIN use yet — practically unreachable, since mandatory
  /// PIN setup already requires one, but kept as a safe fallback) this accepts the entry rather
  /// than blocking a collection there's no way to actually verify offline; the mismatch would
  /// still be caught server-side at sync, same as before this feature existed.
  Future<bool> verify(String pin) async {
    final storedHash = await _secureStorage.read(key: _hashKey);
    final salt = await _secureStorage.read(key: _saltKey);
    if (storedHash == null || salt == null) return true;

    if (_stretch(pin, salt) == storedHash) {
      await _secureStorage.delete(key: _failuresKey);
      await _secureStorage.delete(key: _lockedUntilKey);
      return true;
    }

    final failures = (int.tryParse(await _secureStorage.read(key: _failuresKey) ?? '0') ?? 0) + 1;
    await _secureStorage.write(key: _failuresKey, value: '$failures');
    if (failures >= _lockoutThreshold) {
      await _secureStorage.write(key: _lockedUntilKey, value: DateTime.now().add(_lockoutDuration).toIso8601String());
    }
    return false;
  }

  String _stretch(String pin, String saltHex) {
    List<int> bytes = utf8.encode('$agentId:$pin');
    final salt = _hexToBytes(saltHex);
    for (var i = 0; i < _stretchRounds; i++) {
      bytes = Hmac(sha256, salt).convert(bytes).bytes;
    }
    return base64Encode(bytes);
  }

  String _randomSaltHex() {
    final random = Random.secure();
    return List<int>.generate(16, (_) => random.nextInt(256)).map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }

  List<int> _hexToBytes(String hex) => [for (var i = 0; i < hex.length; i += 2) int.parse(hex.substring(i, i + 2), radix: 16)];
}
