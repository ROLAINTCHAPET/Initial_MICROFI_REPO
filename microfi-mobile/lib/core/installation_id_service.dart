import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:uuid/uuid.dart';

/// Offline Field Collection Security Algorithm v1.1 §3 — the "installation" identity, distinct
/// from [DeviceIdService]'s "device" identity. Deliberately simpler than DeviceIdService (no
/// platform branching, no SSAID): the whole point is a value that does NOT survive an
/// uninstall/reinstall, so a fresh install always presents a fresh installationId even on the
/// same physical device (which DeviceIdService's Android SSAID intentionally would still
/// recognize). Backed by flutter_secure_storage, which on Android is EncryptedSharedPreferences
/// (wiped on uninstall) and on iOS is Keychain (also wiped on uninstall for this app, since
/// nothing here opts into any cross-uninstall persistence flag).
class InstallationIdService {
  static const _key = 'installation_id';
  static const _secretKey = 'installation_hmac_secret';
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();

  Future<String> getInstallationId() async {
    final existing = await _secureStorage.read(key: _key);
    if (existing != null && existing.isNotEmpty) return existing;

    final generated = const Uuid().v4();
    await _secureStorage.write(key: _key, value: generated);
    return generated;
  }

  /// The per-installation HMAC secret for the collection hash-chain (see AuthRepository#login) —
  /// deliberately NOT cleared on sign-out (unlike SessionStorage's token): it belongs to this
  /// *installation*, not to any one login session, and the server only ever re-sends it once, on
  /// the login that creates a brand-new binding. Signing back in on the same installation must
  /// still find it. Only a real uninstall (which wipes secure storage entirely) or an admin
  /// device-binding reset (which naturally overwrites this the next time #saveSecret runs, once
  /// the server issues a fresh one for the new binding) ever invalidates it.
  Future<void> saveSecret(String secret) => _secureStorage.write(key: _secretKey, value: secret);

  Future<String?> readSecret() => _secureStorage.read(key: _secretKey);
}
