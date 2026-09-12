import '../../core/api_client.dart';

class AgentLoginResult {
  final String token;
  final String? installationSecret;

  AgentLoginResult({required this.token, this.installationSecret});
}

/// UC-01 — Agent Login. Mirrors AuthenticationController#login exactly: username, password, the
/// device's IMEI, and the app's own installationId (see InstallationIdService — a distinct
/// identity from the device, per the Offline Field Collection Security Algorithm spec). IMEI is
/// only enforced server-side if the agent has one bound on their account (BR-Auth-02) — a blank
/// value here is fine for agents enrolled without device binding. The transaction PIN is never
/// part of login — see CollectionRepository for where it's checked.
class AuthRepository {
  final ApiClient _client = ApiClient();

  /// Returns the JWT and, only on the login that creates a brand-new installation binding
  /// (first-ever login, or the first login after an admin device-binding reset), the per-
  /// installation HMAC secret for the collection hash-chain — null on every other login.
  Future<AgentLoginResult> login({
    required String username,
    required String password,
    required String imei,
    required String installationId,
  }) async {
    final response = await _client.post('/auth/agent/login', {
      'username': username,
      'password': password,
      'imei': imei,
      'installationId': installationId,
    });
    return AgentLoginResult(
      token: response['token'] as String,
      installationSecret: response['installationSecret'] as String?,
    );
  }

  /// Step 1 of self-service password recovery — always resolves, regardless of whether
  /// [username] exists (see AgentPasswordResetService#requestReset), so there's no distinct
  /// "unknown username" case to handle here beyond a plain network/server error.
  Future<void> requestPasswordReset({required String username}) async {
    await _client.post('/auth/agent/forgot-password', {'username': username});
  }

  /// Step 2 — confirms the SMS code and sets the new login password.
  Future<void> confirmPasswordReset({
    required String username,
    required String otp,
    required String newPassword,
  }) async {
    await _client.post('/auth/agent/reset-password', {
      'username': username,
      'otp': otp,
      'newPassword': newPassword,
    });
  }
}
