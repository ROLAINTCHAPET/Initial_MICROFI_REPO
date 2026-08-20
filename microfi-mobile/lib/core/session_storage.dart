import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Persists the signed-in JWT across app restarts — agent or client, distinguished by the token's
/// own `principalType` claim (see jwt.dart), not by separate storage. The token itself is the only
/// thing worth protecting client-side — everything else (employee code, branch, role) is just
/// claims decoded from it for display, not a separate secret.
class SessionStorage {
  static const _tokenKey = 'microfi_agent_token';
  final _storage = const FlutterSecureStorage();

  Future<void> saveToken(String token) => _storage.write(key: _tokenKey, value: token);

  Future<String?> readToken() => _storage.read(key: _tokenKey);

  Future<void> clear() => _storage.delete(key: _tokenKey);
}
