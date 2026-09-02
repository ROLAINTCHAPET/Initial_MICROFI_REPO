import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/api_client.dart';
import '../../core/design_tokens.dart';
import 'client_repository.dart';
import '../../l10n/app_localizations.dart';

/// Client self-service PIN recovery (UC-19 counterpart to the agent's own forgot-password flow,
/// see auth/forgot_password_screen.dart) — no agent/admin involved, verified purely by an SMS
/// one-time code. Two steps in one screen rather than two routes, since the second step needs the
/// login the first step already collected.
class ClientForgotPinScreen extends StatefulWidget {
  const ClientForgotPinScreen({super.key});

  @override
  State<ClientForgotPinScreen> createState() => _ClientForgotPinScreenState();
}

enum _Step { request, confirm, done }

class _ClientForgotPinScreenState extends State<ClientForgotPinScreen> {
  final _requestFormKey = GlobalKey<FormState>();
  final _confirmFormKey = GlobalKey<FormState>();
  final _loginController = TextEditingController();
  final _otpController = TextEditingController();
  final _newPinController = TextEditingController();
  final _confirmPinController = TextEditingController();
  final _repository = ClientAuthRepository();

  _Step _step = _Step.request;
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _loginController.dispose();
    _otpController.dispose();
    _newPinController.dispose();
    _confirmPinController.dispose();
    super.dispose();
  }

  Future<void> _submitRequest() async {
    if (!_requestFormKey.currentState!.validate()) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await _repository.requestPinReset(login: _loginController.text.trim());
      if (!mounted) return;
      setState(() => _step = _Step.confirm);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = l10n.errorUnableToReachServerShort);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submitConfirm() async {
    if (!_confirmFormKey.currentState!.validate()) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await _repository.confirmPinReset(
        login: _loginController.text.trim(),
        otp: _otpController.text.trim(),
        newPin: _newPinController.text,
      );
      if (!mounted) return;
      setState(() => _step = _Step.done);
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
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        foregroundColor: MicrofiColors.primary,
        elevation: 0,
        title: Text(l10n.cfpTitle, style: const TextStyle(color: MicrofiColors.primary)),
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(
                  color: MicrofiColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(MicrofiRadius.md),
                  border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
                ),
                child: switch (_step) {
                  _Step.request => _buildRequestStep(l10n),
                  _Step.confirm => _buildConfirmStep(l10n),
                  _Step.done => _buildDoneStep(l10n),
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRequestStep(AppLocalizations l10n) {
    return Form(
      key: _requestFormKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(l10n.cfpRequestSubtitle, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
          const SizedBox(height: 16),
          TextFormField(
            controller: _loginController,
            decoration: InputDecoration(labelText: l10n.cfpLoginLabel, prefixIcon: const Icon(Icons.person_outline)),
            autocorrect: false,
            validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
          ),
          _buildErrorBanner(),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: _loading ? null : _submitRequest,
            child: _loading ? _spinner() : Text(l10n.cfpSendCodeButton),
          ),
          const SizedBox(height: 12),
          Center(
            child: TextButton(
              onPressed: _loading ? null : () => Navigator.of(context).pop(),
              child: Text(l10n.cfpBackToLogin),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildConfirmStep(AppLocalizations l10n) {
    return Form(
      key: _confirmFormKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: MicrofiColors.primaryContainer.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(MicrofiRadius.sm),
            ),
            child: Row(
              children: [
                const Icon(Icons.sms_outlined, color: MicrofiColors.primary, size: 16),
                const SizedBox(width: 6),
                Expanded(child: Text(l10n.cfpCodeSentMessage, style: const TextStyle(fontSize: 12, color: MicrofiColors.primary))),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Text(l10n.cfpConfirmSubtitle, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
          const SizedBox(height: 16),
          TextFormField(
            controller: _otpController,
            decoration: InputDecoration(labelText: l10n.cfpOtpLabel, prefixIcon: const Icon(Icons.pin_outlined)),
            keyboardType: TextInputType.number,
            validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _newPinController,
            decoration: InputDecoration(labelText: l10n.cfpNewPinLabel, prefixIcon: const Icon(Icons.lock_outline)),
            obscureText: true,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            validator: (v) {
              if (v == null || v.isEmpty) return l10n.commonRequiredField;
              if (!RegExp(r'^\d{4,6}$').hasMatch(v)) return l10n.cfpPinInvalidFormat;
              return null;
            },
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _confirmPinController,
            decoration: InputDecoration(labelText: l10n.cfpConfirmPinLabel, prefixIcon: const Icon(Icons.lock_outline)),
            obscureText: true,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            validator: (v) {
              if (v == null || v.isEmpty) return l10n.commonRequiredField;
              if (v != _newPinController.text) return l10n.cfpPinMismatch;
              return null;
            },
          ),
          _buildErrorBanner(),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: _loading ? null : _submitConfirm,
            child: _loading ? _spinner() : Text(l10n.cfpResetButton),
          ),
          const SizedBox(height: 12),
          Center(
            child: TextButton(
              onPressed: _loading
                  ? null
                  : () => setState(() {
                        _step = _Step.request;
                        _error = null;
                      }),
              child: Text(l10n.cfpResendCode),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDoneStep(AppLocalizations l10n) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Icon(Icons.check_circle_outline, color: MicrofiColors.primary, size: 40),
        const SizedBox(height: 12),
        Text(l10n.cfpSuccessMessage, textAlign: TextAlign.center, style: const TextStyle(fontSize: 14)),
        const SizedBox(height: 18),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.cfpBackToLogin),
        ),
      ],
    );
  }

  Widget _buildErrorBanner() {
    if (_error == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: Container(
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
    );
  }

  Widget _spinner() => const SizedBox(
        width: 20,
        height: 20,
        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
      );
}
