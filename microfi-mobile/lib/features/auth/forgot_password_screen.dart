import 'package:flutter/material.dart';
import '../../core/api_client.dart';
import '../../core/design_tokens.dart';
import 'auth_repository.dart';
import '../../l10n/app_localizations.dart';

/// Agent self-service password recovery (UC-01 counterpart to the admin-initiated reset) — no
/// admin involved, verified purely by an SMS one-time code. Two steps in one screen rather than
/// two routes, since the second step needs the username the first step already collected.
class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

enum _Step { request, confirm, done }

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final _requestFormKey = GlobalKey<FormState>();
  final _confirmFormKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _otpController = TextEditingController();
  final _newPasswordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _authRepository = AuthRepository();

  _Step _step = _Step.request;
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _usernameController.dispose();
    _otpController.dispose();
    _newPasswordController.dispose();
    _confirmPasswordController.dispose();
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
      await _authRepository.requestPasswordReset(username: _usernameController.text.trim());
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
      await _authRepository.confirmPasswordReset(
        username: _usernameController.text.trim(),
        otp: _otpController.text.trim(),
        newPassword: _newPasswordController.text,
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
        title: Text(l10n.fpTitle, style: const TextStyle(color: MicrofiColors.primary)),
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
          Text(l10n.fpRequestSubtitle, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
          const SizedBox(height: 16),
          TextFormField(
            controller: _usernameController,
            decoration: InputDecoration(labelText: l10n.fpUsernameLabel, prefixIcon: const Icon(Icons.person_outline)),
            autocorrect: false,
            validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
          ),
          _buildErrorBanner(),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: _loading ? null : _submitRequest,
            child: _loading ? _spinner() : Text(l10n.fpSendCodeButton),
          ),
          const SizedBox(height: 12),
          Center(
            child: TextButton(
              onPressed: _loading ? null : () => Navigator.of(context).pop(),
              child: Text(l10n.fpBackToLogin),
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
                Expanded(child: Text(l10n.fpCodeSentMessage, style: const TextStyle(fontSize: 12, color: MicrofiColors.primary))),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Text(l10n.fpConfirmSubtitle, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
          const SizedBox(height: 16),
          TextFormField(
            controller: _otpController,
            decoration: InputDecoration(labelText: l10n.fpOtpLabel, prefixIcon: const Icon(Icons.pin_outlined)),
            keyboardType: TextInputType.number,
            validator: (v) => (v == null || v.trim().isEmpty) ? l10n.commonRequiredField : null,
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _newPasswordController,
            decoration: InputDecoration(labelText: l10n.fpNewPasswordLabel, prefixIcon: const Icon(Icons.lock_outline)),
            obscureText: true,
            validator: (v) {
              if (v == null || v.isEmpty) return l10n.commonRequiredField;
              if (v.length < 8) return l10n.fpPasswordTooShort;
              return null;
            },
          ),
          const SizedBox(height: 12),
          TextFormField(
            controller: _confirmPasswordController,
            decoration: InputDecoration(labelText: l10n.fpConfirmPasswordLabel, prefixIcon: const Icon(Icons.lock_outline)),
            obscureText: true,
            validator: (v) {
              if (v == null || v.isEmpty) return l10n.commonRequiredField;
              if (v != _newPasswordController.text) return l10n.fpPasswordMismatch;
              return null;
            },
          ),
          _buildErrorBanner(),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: _loading ? null : _submitConfirm,
            child: _loading ? _spinner() : Text(l10n.fpResetButton),
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
              child: Text(l10n.fpResendCode),
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
        Text(l10n.fpSuccessMessage, textAlign: TextAlign.center, style: const TextStyle(fontSize: 14)),
        const SizedBox(height: 18),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.fpBackToLogin),
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
