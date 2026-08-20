import 'package:flutter/material.dart';
import 'app_shell.dart';
import 'design_tokens.dart';
import 'location_tracking_service.dart';
import 'session_storage.dart';
import '../features/auth/login_screen.dart';
import '../features/auth/pin_setup_screen.dart';
import '../features/home/agent_profile.dart';
import '../features/home/home_repository.dart';

/// Resolves the agent's own profile once after login/session-restore, then hands off to AppShell
/// — every screen downstream assumes this is already known rather than re-fetching it.
class SessionEntry extends StatefulWidget {
  final String token;

  const SessionEntry({super.key, required this.token});

  @override
  State<SessionEntry> createState() => _SessionEntryState();
}

class _SessionEntryState extends State<SessionEntry> {
  AgentProfile? _profile;
  String? _error;
  LocationTrackingService? _tracking;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _tracking?.stop();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final profile = await HomeRepository(widget.token).fetchMyProfile();
      if (!mounted) return;
      setState(() => _profile = profile);
      _maybeStartTracking();
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = 'Unable to load your profile: $e');
    }
  }

  // UC-10: starts once the agent actually has an open, usable session — not while a mandatory
  // PIN setup is still blocking them — and only once (start() itself is also idempotent).
  void _maybeStartTracking() {
    final profile = _profile;
    if (profile == null || profile.pinMustChange || _tracking != null) return;
    _tracking = LocationTrackingService(token: widget.token, agentId: profile.id)..start();
  }

  Future<void> _signOut() async {
    _tracking?.stop();
    await SessionStorage().clear();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const LoginScreen()));
  }

  @override
  Widget build(BuildContext context) {
    if (_error != null) {
      return Scaffold(
        backgroundColor: Colors.transparent,
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, color: MicrofiColors.error, size: 40),
                const SizedBox(height: 12),
                Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: MicrofiColors.error)),
                const SizedBox(height: 16),
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
    if (_profile!.pinMustChange) {
      return PinSetupScreen(
        token: widget.token,
        profile: _profile!,
        mandatory: true,
        onChanged: (updated) {
          setState(() => _profile = updated);
          _maybeStartTracking();
        },
        key: ValueKey('pin-setup-${_profile!.id}'),
      );
    }
    return AppShell(token: widget.token, profile: _profile!);
  }
}
