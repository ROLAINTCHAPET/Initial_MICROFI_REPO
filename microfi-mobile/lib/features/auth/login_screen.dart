import 'package:flutter/material.dart';
import '../../core/api_client.dart';
import '../../core/design_tokens.dart';
import '../../core/device_id_service.dart';
import '../../core/location.dart';
import '../../core/session_entry.dart';
import '../../core/session_storage.dart';
import 'auth_repository.dart';
import 'forgot_password_screen.dart';
import '../../l10n/app_localizations.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  final _authRepository = AuthRepository();
  final _sessionStorage = SessionStorage();
  final _deviceIdService = DeviceIdService();

  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      // BR-05/FR-12's mandatory-GPS rule applied at the front door too, not just at the
      // collection step — an agent whose location isn't on/available can't get into the app
      // at all, so it can never be forgotten once they're already inside for the day.
      await captureCurrentLocation();
      // Device id is captured automatically, never typed by the agent — see DeviceIdService.
      final deviceId = await _deviceIdService.getDeviceId();
      final token = await _authRepository.login(
        username: _usernameController.text.trim(),
        password: _passwordController.text,
        imei: deviceId,
      );
      await _sessionStorage.saveToken(token);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => SessionEntry(token: token)),
      );
    } on LocationUnavailable catch (e) {
      setState(() => _error = e.message(l10n));
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = l10n.errorUnableToReachServerShort);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(backgroundColor: Colors.transparent, foregroundColor: MicrofiColors.primary, elevation: 0),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: MicrofiColors.primary,
                      borderRadius: BorderRadius.circular(MicrofiRadius.md),
                    ),
                    child: const Icon(Icons.account_balance, color: Colors.white, size: 26),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    l10n.lgMicrofiAgent,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    l10n.lgFieldCollectionCashDesk,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant),
                  ),
                  const SizedBox(height: 18),
                  Container(
                    padding: const EdgeInsets.all(18),
                    decoration: BoxDecoration(
                      color: MicrofiColors.surfaceContainerLowest,
                      borderRadius: BorderRadius.circular(MicrofiRadius.md),
                      border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
                    ),
                    child: Form(
                      key: _formKey,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          TextFormField(
                            controller: _usernameController,
                            decoration: InputDecoration(
                              labelText: l10n.lgUsernameLabel,
                              prefixIcon: const Icon(Icons.person_outline),
                            ),
                            autocorrect: false,
                            validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
                          ),
                          const SizedBox(height: 12),
                          TextFormField(
                            controller: _passwordController,
                            decoration: InputDecoration(
                              labelText: l10n.lgPasswordLabel,
                              prefixIcon: const Icon(Icons.lock_outline),
                            ),
                            obscureText: true,
                            validator: (v) => (v == null || v.isEmpty) ? l10n.commonRequiredField : null,
                          ),
                          if (_error != null) ...[
                            const SizedBox(height: 12),
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: MicrofiColors.errorContainer,
                                borderRadius: BorderRadius.circular(MicrofiRadius.sm),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.error_outline, color: MicrofiColors.onErrorContainer, size: 16),
                                  const SizedBox(width: 6),
                                  Expanded(
                                    child: Text(_error!, style: const TextStyle(fontSize: 12, color: MicrofiColors.onErrorContainer)),
                                  ),
                                ],
                              ),
                            ),
                          ],
                          const SizedBox(height: 18),
                          FilledButton(
                            onPressed: _loading ? null : _submit,
                            child: _loading
                                ? const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                  )
                                : Text(l10n.clSignInButton),
                          ),
                          const SizedBox(height: 8),
                          Center(
                            child: TextButton(
                              onPressed: _loading
                                  ? null
                                  : () => Navigator.of(context).push(
                                        MaterialPageRoute(builder: (_) => const ForgotPasswordScreen()),
                                      ),
                              child: Text(l10n.lgForgotPasswordLink),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.shield_outlined, size: 16, color: MicrofiColors.onSurfaceVariant),
                      const SizedBox(width: 6),
                      Flexible(
                        child: Text(
                          l10n.lgSecureAccessOnly,
                          textAlign: TextAlign.center,
                          style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
