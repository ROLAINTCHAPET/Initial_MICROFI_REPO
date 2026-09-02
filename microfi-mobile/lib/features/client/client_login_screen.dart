import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../../core/session_storage.dart';
import 'client_activation_screen.dart';
import 'client_forgot_pin_screen.dart';
import 'client_repository.dart';
import 'client_session_entry.dart';
import '../../l10n/app_localizations.dart';

class ClientLoginScreen extends StatefulWidget {
  const ClientLoginScreen({super.key});

  @override
  State<ClientLoginScreen> createState() => _ClientLoginScreenState();
}

class _ClientLoginScreenState extends State<ClientLoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _loginController = TextEditingController();
  final _pinController = TextEditingController();
  final _repository = ClientAuthRepository();
  final _sessionStorage = SessionStorage();

  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _loginController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final token = await _repository.login(login: _loginController.text.trim(), pin: _pinController.text);
      await _sessionStorage.saveToken(token);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => ClientSessionEntry(token: token)));
    } catch (e) {
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.clClientLoginTitle)),
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
                    child: const Icon(Icons.menu_book_outlined, color: Colors.white, size: 26),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    l10n.clMyBooklet,
                    textAlign: TextAlign.center,
                    style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    l10n.clDigitalSavingsBooklet,
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
                            controller: _loginController,
                            decoration: InputDecoration(
                              labelText: l10n.clLoginLabel,
                              prefixIcon: const Icon(Icons.person_outline),
                            ),
                            validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
                          ),
                          const SizedBox(height: 12),
                          TextFormField(
                            controller: _pinController,
                            decoration: InputDecoration(
                              labelText: l10n.clPinLabel,
                              prefixIcon: const Icon(Icons.lock_outline),
                            ),
                            obscureText: true,
                            keyboardType: TextInputType.number,
                            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                            validator: (v) => (v == null || v.length < 4) ? l10n.clPinMinDigitsError : null,
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
                                  Expanded(child: Text(_error!, style: const TextStyle(fontSize: 12, color: MicrofiColors.onErrorContainer))),
                                ],
                              ),
                            ),
                          ],
                          const SizedBox(height: 18),
                          FilledButton(
                            onPressed: _loading ? null : _submit,
                            child: _loading
                                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                                : Text(l10n.clSignInButton),
                          ),
                          const SizedBox(height: 8),
                          Center(
                            child: TextButton(
                              onPressed: _loading
                                  ? null
                                  : () => Navigator.of(context).push(
                                        MaterialPageRoute(builder: (_) => const ClientForgotPinScreen()),
                                      ),
                              child: Text(l10n.clForgotPinLink),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Center(
                    child: TextButton(
                      onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ClientActivationScreen())),
                      child: Text(l10n.clFirstTimeActivate, style: const TextStyle(fontSize: 13)),
                    ),
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
