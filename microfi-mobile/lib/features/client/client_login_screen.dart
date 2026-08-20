import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../../core/session_storage.dart';
import 'client_activation_screen.dart';
import 'client_repository.dart';
import 'client_session_entry.dart';

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
      setState(() => _error = friendlyErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: const Text('Client Login')),
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
                  const Text(
                    'My Booklet',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                  ),
                  const SizedBox(height: 3),
                  const Text(
                    'Your digital savings booklet',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant),
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
                            decoration: const InputDecoration(
                              labelText: 'Login',
                              prefixIcon: Icon(Icons.person_outline),
                            ),
                            validator: (v) => (v == null || v.trim().isEmpty) ? 'Required' : null,
                          ),
                          const SizedBox(height: 12),
                          TextFormField(
                            controller: _pinController,
                            decoration: const InputDecoration(
                              labelText: 'PIN',
                              prefixIcon: Icon(Icons.lock_outline),
                            ),
                            obscureText: true,
                            keyboardType: TextInputType.number,
                            validator: (v) => (v == null || v.length < 4) ? 'Min. 4 digits' : null,
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
                                : const Text('Sign In'),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Center(
                    child: TextButton(
                      onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ClientActivationScreen())),
                      child: const Text('First time? Activate my booklet', style: TextStyle(fontSize: 13)),
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
