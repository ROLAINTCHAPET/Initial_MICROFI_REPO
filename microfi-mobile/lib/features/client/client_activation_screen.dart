import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_repository.dart';
import '../../l10n/app_localizations.dart';

/// UC-19 step 1 of the two-party activation gate: the client sets their own login/PIN here.
/// Steps 2/3 (agent sponsorship + the client's own payment confirmation) happen afterwards — the
/// 365-day booklet token isn't issued until both are done.
class ClientActivationScreen extends StatefulWidget {
  const ClientActivationScreen({super.key});

  @override
  State<ClientActivationScreen> createState() => _ClientActivationScreenState();
}

class _ClientActivationScreenState extends State<ClientActivationScreen> {
  final _formKey = GlobalKey<FormState>();
  final _activationIdController = TextEditingController();
  final _loginController = TextEditingController();
  final _pinController = TextEditingController();
  final _repository = ClientAuthRepository();

  bool _loading = false;
  String? _successMessage;

  @override
  void dispose() {
    _activationIdController.dispose();
    _loginController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      final message = await _repository.activate(
        activationId: _activationIdController.text.trim(),
        login: _loginController.text.trim(),
        pin: _pinController.text,
      );
      if (!mounted) return;
      setState(() => _successMessage = message);
    } catch (e) {
      if (!mounted) return;
      await showErrorDialog(context, e, title: AppLocalizations.of(context)!.caActivationFailedTitle);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.caActivateMyBookletTitle)),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: _successMessage != null ? _buildSuccess(l10n) : _buildForm(l10n),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildSuccess(AppLocalizations l10n) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 52,
          height: 52,
          decoration: const BoxDecoration(color: MicrofiColors.secondary, shape: BoxShape.circle),
          child: const Icon(Icons.check, color: Colors.white, size: 26),
        ),
        const SizedBox(height: 14),
        Text(l10n.caCredentialsSetTitle, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
        const SizedBox(height: 8),
        Text(_successMessage!, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
        const SizedBox(height: 18),
        SizedBox(
          width: double.infinity,
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.caBackToLogin),
          ),
        ),
      ],
    );
  }

  Widget _buildForm(AppLocalizations l10n) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.all(MicrofiSpacing.card),
          decoration: BoxDecoration(
            color: MicrofiColors.surfaceContainerLow,
            borderRadius: BorderRadius.circular(MicrofiRadius.md),
          ),
          child: Text(
            l10n.caIntroMessage,
            style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant),
          ),
        ),
        const SizedBox(height: MicrofiSpacing.gapLg),
        Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              TextFormField(
                controller: _activationIdController,
                decoration: InputDecoration(labelText: l10n.caActivationIdLabel, prefixIcon: const Icon(Icons.qr_code)),
                validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _loginController,
                decoration: InputDecoration(labelText: l10n.caChooseLoginLabel, prefixIcon: const Icon(Icons.person_outline)),
                validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _pinController,
                decoration: InputDecoration(labelText: l10n.caChoosePinLabel, prefixIcon: const Icon(Icons.lock_outline)),
                obscureText: true,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                validator: (v) => (v == null || v.length < 4 || v.length > 6) ? l10n.caPinDigitsError : null,
              ),
              const SizedBox(height: 18),
              FilledButton(
                onPressed: _loading ? null : _submit,
                child: _loading
                    ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : Text(l10n.caSetCredentialsButton),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
