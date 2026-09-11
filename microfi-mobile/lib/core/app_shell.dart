import 'dart:async';

import 'package:flutter/material.dart';
import 'connectivity_service.dart';
import 'design_tokens.dart';
import 'local_ceiling_cache.dart';
import 'local_geofence_cache.dart';
import 'local_pin_verifier.dart';
import 'local_schedule_cache.dart';
import 'session_storage.dart';
import '../features/auth/login_screen.dart';
import '../features/history/history_screen.dart';
import '../features/home/agent_profile.dart';
import '../features/home/home_screen.dart';
import '../features/profile/profile_screen.dart';
import '../features/wallet/wallet_screen.dart';
import '../l10n/app_localizations.dart';

/// The persistent header ("MICROFI COLLECT", live connectivity status, account menu) and
/// BottomNavBar (Home/History/Wallet) that wrap every agent-facing screen once logged in. Tabs are
/// rebuilt (not IndexedStack-preserved) on every switch so escrow/collections data is never stale
/// — the same staleness bug already found and fixed once in the Home/collection-form flow.
/// No separate Emergency tab: SOS lives directly on Home (one tap, no confirmation) rather than
/// behind another navigation step.
class AppShell extends StatefulWidget {
  final String token;
  final AgentProfile profile;

  const AppShell({super.key, required this.token, required this.profile});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _selectedIndex = 0;
  bool _online = true;
  StreamSubscription<bool>? _connectivitySub;

  static const _icons = [Icons.home_rounded, Icons.history_rounded, Icons.account_balance_wallet_rounded];

  @override
  void initState() {
    super.initState();
    ConnectivityService.instance.isOnline().then((online) {
      if (mounted) setState(() => _online = online);
    });
    _connectivitySub = ConnectivityService.instance.onOnlineChanged.listen((online) {
      if (mounted) setState(() => _online = online);
    });
  }

  @override
  void dispose() {
    _connectivitySub?.cancel();
    super.dispose();
  }

  Future<void> _signOut() async {
    // The offline collection queue is deliberately left untouched here — those rows are cash
    // already collected and not yet synced, and must survive a sign-out exactly like they survive
    // an app restart. Only the per-agent local caches (PIN verification, ceiling snapshot,
    // geofence snapshot, schedule snapshot) are cleared, since they have no reason to keep
    // existing once this agent isn't the one signed in on this device.
    await LocalPinVerifier(widget.profile.id).clear();
    await LocalCeilingCache(widget.profile.id).clear();
    await LocalGeofenceCache(widget.profile.id).clear();
    await LocalScheduleCache(widget.profile.id).clear();
    await SessionStorage().clear();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const LoginScreen()));
  }

  void _openProfile() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => ProfileScreen(token: widget.token, profile: widget.profile)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final tabs = [l10n.asHomeTab, l10n.asHistoryTab, l10n.wsWalletTitle];
    return Scaffold(
      backgroundColor: Colors.transparent,
      extendBodyBehindAppBar: false,
      appBar: AppBar(
        backgroundColor: MicrofiColors.primary,
        foregroundColor: MicrofiColors.onPrimary,
        elevation: 8,
        shadowColor: Colors.black.withValues(alpha: 0.25),
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.only(bottomLeft: Radius.circular(MicrofiRadius.lg), bottomRight: Radius.circular(MicrofiRadius.lg)),
        ),
        automaticallyImplyLeading: false,
        titleSpacing: 20,
        title: Text(
          l10n.asAppTitle,
          style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17, letterSpacing: 0.3),
        ),
        actions: [
          Tooltip(
            message: _online ? l10n.hsStatusActive : l10n.asOfflineTooltip,
            child: Container(
              width: 32,
              height: 32,
              margin: const EdgeInsets.only(right: 4),
              alignment: Alignment.center,
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.12), shape: BoxShape.circle),
              child: Icon(
                _online ? Icons.wifi_rounded : Icons.wifi_off_rounded,
                size: 18,
                color: _online ? MicrofiColors.secondaryFixed : MicrofiColors.tertiaryFixedDim,
              ),
            ),
          ),
          PopupMenuButton<String>(
            icon: Container(
              width: 32,
              height: 32,
              alignment: Alignment.center,
              decoration: const BoxDecoration(color: MicrofiColors.surfaceContainerLowest, shape: BoxShape.circle),
              child: const Icon(Icons.person_rounded, color: MicrofiColors.primary, size: 18),
            ),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
            onSelected: (value) {
              if (value == 'profile') _openProfile();
              if (value == 'signout') _signOut();
            },
            itemBuilder: (context) => [
              PopupMenuItem(value: 'profile', child: Row(children: [const Icon(Icons.person_outline, size: 20), const SizedBox(width: 10), Text(l10n.prMyProfileTitle)])),
              PopupMenuItem(
                value: 'signout',
                child: Row(children: [const Icon(Icons.logout, size: 20, color: MicrofiColors.error), const SizedBox(width: 10), Text(l10n.commonSignOut, style: const TextStyle(color: MicrofiColors.error))]),
              ),
            ],
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(child: _buildTab()),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          color: MicrofiColors.surfaceContainerLowest,
          borderRadius: const BorderRadius.only(topLeft: Radius.circular(MicrofiRadius.lg), topRight: Radius.circular(MicrofiRadius.lg)),
          boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.08), blurRadius: 16, offset: const Offset(0, -4))],
        ),
        clipBehavior: Clip.antiAlias,
        child: NavigationBarTheme(
          data: NavigationBarThemeData(
            indicatorColor: MicrofiColors.secondaryFixed,
            indicatorShape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.full)),
            labelTextStyle: WidgetStateProperty.resolveWith(
              (states) => TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w600,
                color: states.contains(WidgetState.selected) ? MicrofiColors.onSecondaryFixedVariant : MicrofiColors.onSurfaceVariant,
              ),
            ),
          ),
          child: NavigationBar(
            height: 64,
            backgroundColor: Colors.transparent,
            elevation: 0,
            selectedIndex: _selectedIndex,
            onDestinationSelected: (index) => setState(() => _selectedIndex = index),
            destinations: List.generate(
              tabs.length,
              (i) => NavigationDestination(icon: Icon(_icons[i]), label: tabs[i]),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTab() {
    switch (_selectedIndex) {
      case 1:
        return HistoryScreen(key: UniqueKey(), token: widget.token);
      case 2:
        return WalletScreen(key: UniqueKey(), token: widget.token, agentId: widget.profile.id);
      case 0:
      default:
        return HomeScreen(key: UniqueKey(), token: widget.token);
    }
  }
}
