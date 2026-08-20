import 'package:flutter/material.dart';
import '../../core/api_client.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../home/agent_profile.dart';
import '../home/home_repository.dart';

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
    if (_newPinController.text != _confirmPinController.text) {
      setState(() => _error = 'New PIN and confirmation do not match.');
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
      if (!mounted) return;
      if (widget.mandatory) {
        widget.onChanged?.call(updated);
      } else {
        await showSuccessDialog(context, 'Your PIN has been updated.');
        if (!mounted) return;
        Navigator.of(context).pop(updated);
      }
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (e) {
      setState(() => _error = friendlyErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !widget.mandatory,
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: widget.mandatory ? null : AppBar(title: const Text('Change PIN')),
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
                      const Text(
                        'Set Your Transaction PIN',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        'Your branch assigned a starting PIN. Replace it with one only you know before you can record a collection.',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant),
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
                                labelText: widget.mandatory ? 'Starting PIN (given by your branch)' : 'Current PIN',
                                prefixIcon: const Icon(Icons.lock_outline),
                              ),
                              obscureText: true,
                              keyboardType: TextInputType.number,
                              validator: (v) => (v == null || v.length < 4) ? 'Min. 4 digits' : null,
                            ),
                            const SizedBox(height: 12),
                            TextFormField(
                              controller: _newPinController,
                              decoration: const InputDecoration(
                                labelText: 'New PIN',
                                prefixIcon: Icon(Icons.lock_reset_outlined),
                                helperText: 'Not all the same digit or a simple run (e.g. 1234)',
                              ),
                              obscureText: true,
                              keyboardType: TextInputType.number,
                              validator: (v) => (v == null || v.length < 4 || v.length > 10) ? '4–10 digits' : null,
                            ),
                            const SizedBox(height: 12),
                            TextFormField(
                              controller: _confirmPinController,
                              decoration: const InputDecoration(
                                labelText: 'Confirm New PIN',
                                prefixIcon: Icon(Icons.check_circle_outline),
                              ),
                              obscureText: true,
                              keyboardType: TextInputType.number,
                              validator: (v) => (v == null || v.isEmpty) ? 'Required' : null,
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
                                  : const Text('Set PIN'),
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
