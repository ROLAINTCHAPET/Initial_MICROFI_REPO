import 'dart:async';

import 'package:flutter/material.dart';
import 'connectivity_service.dart';
import 'design_tokens.dart';
import 'local_ceiling_cache.dart';
import 'local_pin_verifier.dart';
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

  static const _icons = [Icons.home, Icons.history, Icons.account_balance_wallet];

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
    // an app restart. Only the per-agent local caches (PIN verification, ceiling snapshot) are
    // cleared, since they have no reason to keep existing once this agent isn't the one signed in
    // on this device.
    await LocalPinVerifier(widget.profile.id).clear();
    await LocalCeilingCache(widget.profile.id).clear();
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
      appBar: AppBar(
        backgroundColor: MicrofiColors.primary,
        foregroundColor: MicrofiColors.onPrimary,
        elevation: 0,
        automaticallyImplyLeading: false,
        titleSpacing: 20,
        title: Text(
          l10n.asAppTitle,
          style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 17, letterSpacing: 0.3),
        ),
        actions: [
          Tooltip(
            message: _online ? l10n.hsStatusActive : l10n.asOfflineTooltip,
            child: Padding(
              padding: const EdgeInsets.only(right: 4),
              child: Icon(
                _online ? Icons.wifi : Icons.wifi_off,
                color: _online ? MicrofiColors.secondaryFixed : MicrofiColors.tertiaryFixedDim,
              ),
            ),
          ),
          PopupMenuButton<String>(
            icon: const Icon(Icons.account_circle_outlined),
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
      bottomNavigationBar: NavigationBarTheme(
        data: NavigationBarThemeData(
          indicatorColor: MicrofiColors.secondaryFixed,
          labelTextStyle: WidgetStateProperty.resolveWith(
            (states) => TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: states.contains(WidgetState.selected) ? MicrofiColors.onSecondaryFixedVariant : MicrofiColors.onSurfaceVariant,
            ),
          ),
        ),
        child: NavigationBar(
          height: 62,
          backgroundColor: MicrofiColors.surfaceContainerLowest,
          selectedIndex: _selectedIndex,
          onDestinationSelected: (index) => setState(() => _selectedIndex = index),
          destinations: List.generate(
            tabs.length,
            (i) => NavigationDestination(icon: Icon(_icons[i]), label: tabs[i]),
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
