import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../core/api_client.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../../core/local_pin_verifier.dart';
import '../home/agent_profile.dart';
import '../home/home_repository.dart';
import '../../l10n/app_localizations.dart';

/// Forced first-time replacement of the admin-assigned starting transaction PIN — not skippable,
/// shown by SessionEntry whenever AgentProfile.pinMustChange is true. The same screen also serves
/// any later voluntary PIN change (see ProfileScreen), just without the "you must do this" framing.
class PinSetupScreen extends StatefulWidget {
  final String token;
  final AgentProfile profile;
  final bool mandatory;

  /// Mandatory usage renders this screen directly as the tree root (SessionEntry, before
  /// AppShell exists) rather than pushing it via Navigator — so success is reported through this
  /// callback instead of a pop, which would have nothing to pop back to.
  final ValueChanged<AgentProfile>? onChanged;

  const PinSetupScreen({super.key, required this.token, required this.profile, this.mandatory = true, this.onChanged});

  @override
  State<PinSetupScreen> createState() => _PinSetupScreenState();
}

class _PinSetupScreenState extends State<PinSetupScreen> {
  final _formKey = GlobalKey<FormState>();
  final _currentPinController = TextEditingController();
  final _newPinController = TextEditingController();
  final _confirmPinController = TextEditingController();
  late final HomeRepository _repository = HomeRepository(widget.token);

  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _currentPinController.dispose();
    _newPinController.dispose();
    _confirmPinController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    final l10n = AppLocalizations.of(context)!;
    if (_newPinController.text != _confirmPinController.text) {
      setState(() => _error = l10n.psPinMismatchError);
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final updated = await _repository.changePin(
        currentPin: _currentPinController.text,
        newPin: _newPinController.text,
      );
      await LocalPinVerifier(widget.profile.id).seed(_newPinController.text);
      if (!mounted) return;
      if (widget.mandatory) {
        widget.onChanged?.call(updated);
      } else {
        await showSuccessDialog(context, l10n.psPinUpdatedMessage);
        if (!mounted) return;
        Navigator.of(context).pop(updated);
      }
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (e) {
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return PopScope(
      canPop: !widget.mandatory,
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: widget.mandatory ? null : AppBar(title: Text(l10n.psChangePinTitle)),
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
                    if (widget.mandatory) ...[
                      Container(
                        width: 52,
                        height: 52,
                        alignment: Alignment.center,
                        decoration: BoxDecoration(color: MicrofiColors.primary, borderRadius: BorderRadius.circular(MicrofiRadius.md)),
                        child: const Icon(Icons.pin_outlined, color: Colors.white, size: 26),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        l10n.psSetYourPinTitle,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        l10n.psSetYourPinIntro,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant),
                      ),
                      const SizedBox(height: 18),
                    ],
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
                              controller: _currentPinController,
                              decoration: InputDecoration(
                                labelText: widget.mandatory ? l10n.psStartingPinLabel : l10n.psCurrentPinLabel,
                                prefixIcon: const Icon(Icons.lock_outline),
                              ),
                              obscureText: true,
                              keyboardType: TextInputType.number,
                              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                              validator: (v) => (v == null || v.length < 4) ? l10n.clPinMinDigitsError : null,
                            ),
                            const SizedBox(height: 12),
                            TextFormField(
                              controller: _newPinController,
                              decoration: InputDecoration(
                                labelText: l10n.psNewPinLabel,
                                prefixIcon: const Icon(Icons.lock_reset_outlined),
                                helperText: l10n.psNewPinHelperText,
                              ),
                              obscureText: true,
                              keyboardType: TextInputType.number,
                              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                              validator: (v) => (v == null || v.length < 4 || v.length > 10) ? l10n.psPinLengthError : null,
                            ),
                            const SizedBox(height: 12),
                            TextFormField(
                              controller: _confirmPinController,
                              decoration: InputDecoration(
                                labelText: l10n.psConfirmNewPinLabel,
                                prefixIcon: const Icon(Icons.check_circle_outline),
                              ),
                              obscureText: true,
                              keyboardType: TextInputType.number,
                              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                              validator: (v) => (v == null || v.isEmpty) ? l10n.commonRequiredField : null,
                            ),
                            if (_error != null) ...[
                              const SizedBox(height: 12),
                              Container(
                                padding: const EdgeInsets.all(10),
                                decoration: BoxDecoration(color: MicrofiColors.errorContainer, borderRadius: BorderRadius.circular(MicrofiRadius.sm)),
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
                                  : Text(l10n.psSetPinButton),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
