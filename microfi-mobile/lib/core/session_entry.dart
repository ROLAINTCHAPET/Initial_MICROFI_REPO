import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'app_shell.dart';
import 'design_tokens.dart';
import 'local_ceiling_cache.dart';
import 'local_pin_verifier.dart';
import 'location.dart';
import 'location_tracking_service.dart';
import 'session_storage.dart';
import '../features/auth/login_screen.dart';
import '../features/auth/pin_setup_screen.dart';
import '../features/home/agent_profile.dart';
import '../features/home/home_repository.dart';
import '../l10n/app_localizations.dart';

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

  // BR-05/FR-12's mandatory-GPS gate, checked here too — not just at login — since a persisted
  // JWT means most days an agent skips LoginScreen entirely and lands straight here. Location
  // has to be re-confirmed every time a session opens, not just the rare full credential login.
  bool _locationReady = false;
  String? _locationError;

  @override
  void initState() {
    super.initState();
    _load();
    _checkLocation();
  }

  @override
  void dispose() {
    _tracking?.stop();
    super.dispose();
  }

  Future<void> _checkLocation() async {
    setState(() => _locationError = null);
    try {
      await captureCurrentLocation();
      if (!mounted) return;
      setState(() => _locationReady = true);
    } on LocationUnavailable catch (e) {
      if (!mounted) return;
      setState(() => _locationError = e.message(AppLocalizations.of(context)!));
    } catch (_) {
      if (!mounted) return;
      setState(() => _locationError = AppLocalizations.of(context)!.seUnableToCheckLocation);
    }
  }

  Future<void> _load() async {
    try {
      final profile = await HomeRepository(widget.token).fetchMyProfile();
      if (!mounted) return;
      setState(() => _profile = profile);
      _maybeStartTracking();
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = AppLocalizations.of(context)!.seUnableToLoadProfile(e.toString()));
    }
  }

  // UC-10: starts once the agent actually has an open, usable session — not while a mandatory
  // PIN setup is still blocking them — and only once (start() itself is also idempotent).
  void _maybeStartTracking() {
    final profile = _profile;
    if (profile == null || profile.pinMustChange || _tracking != null) return;
    _tracking = LocationTrackingService(token: widget.token, agentId: profile.id, l10n: AppLocalizations.of(context)!)..start();
  }

  Future<void> _signOut() async {
    _tracking?.stop();
    // _profile may not have loaded yet (this can fire from the location-error or profile-error
    // screens) — nothing to clear in that case, same reasoning as AppShell._signOut().
    final profile = _profile;
    if (profile != null) {
      await LocalPinVerifier(profile.id).clear();
      await LocalCeilingCache(profile.id).clear();
    }
    await SessionStorage().clear();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const LoginScreen()));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_locationError != null) {
      return Scaffold(
        backgroundColor: Colors.transparent,
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.location_off_outlined, color: MicrofiColors.error, size: 40),
                const SizedBox(height: 12),
                Text(l10n.seLocationRequiredTitle, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                const SizedBox(height: 6),
                Text(_locationError!, textAlign: TextAlign.center, style: const TextStyle(color: MicrofiColors.error)),
                const SizedBox(height: 16),
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    OutlinedButton(onPressed: Geolocator.openAppSettings, child: Text(l10n.seOpenSettings)),
                    const SizedBox(width: 12),
                    FilledButton(onPressed: _checkLocation, child: Text(l10n.commonRetry)),
                  ],
                ),
                TextButton(onPressed: _signOut, child: Text(l10n.commonSignOut)),
              ],
            ),
          ),
        ),
      );
    }
    if (!_locationReady) {
      return const Scaffold(backgroundColor: Colors.transparent, body: Center(child: CircularProgressIndicator()));
    }
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
                FilledButton(onPressed: _signOut, child: Text(l10n.seSignInAgain)),
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
