import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../../core/session_storage.dart';
import '../auth/role_select_screen.dart';
import 'client_models.dart';
import 'client_repository.dart';
import 'client_shell.dart';

/// Resolves the client's own profile once after login/session-restore, then hands off to
/// ClientShell — mirrors SessionEntry's role for the agent side.
class ClientSessionEntry extends StatefulWidget {
  final String token;

  const ClientSessionEntry({super.key, required this.token});

  @override
  State<ClientSessionEntry> createState() => _ClientSessionEntryState();
}

class _ClientSessionEntryState extends State<ClientSessionEntry> {
  ClientSelfProfile? _profile;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final profile = await ClientSelfRepository(widget.token).fetchProfile();
      if (!mounted) return;
      setState(() => _profile = profile);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(e));
    }
  }

  Future<void> _signOut() async {
    await SessionStorage().clear();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const RoleSelectScreen()));
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return Scaffold(
        backgroundColor: Colors.transparent,
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, color: MicrofiColors.error, size: 34),
                const SizedBox(height: 10),
                Text(_error!, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: MicrofiColors.error)),
                const SizedBox(height: 14),
                FilledButton(onPressed: _signOut, child: const Text('Sign In Again')),
              ],
            ),
          ),
        ),
      );
    }
    if (_profile == null) {
      return const Scaffold(backgroundColor: Colors.transparent, body: Center(child: CircularProgressIndicator()));
    }
    return ClientShell(token: widget.token, profile: _profile!);
  }
}
