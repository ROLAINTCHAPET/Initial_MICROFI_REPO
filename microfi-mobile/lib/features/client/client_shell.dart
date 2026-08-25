import 'dart:async';

import 'package:flutter/material.dart';
import '../../core/connectivity_service.dart';
import '../../core/design_tokens.dart';
import '../../core/session_storage.dart';
import '../auth/role_select_screen.dart';
import 'client_history_screen.dart';
import 'client_home_screen.dart';
import 'client_models.dart';
import 'client_wallet_screen.dart';
import '../../l10n/app_localizations.dart';

/// Client-facing counterpart to AppShell: same header/nav language ("MICROFI COLLECT" → "MY
/// BOOKLET", live connectivity, account menu), Home/History/Wallet tabs. No Emergency tab, same
/// reasoning as the agent app: no backend concept of a client-raised SOS to back it.
class ClientShell extends StatefulWidget {
  final String token;
  final ClientSelfProfile profile;

  const ClientShell({super.key, required this.token, required this.profile});

  @override
  State<ClientShell> createState() => _ClientShellState();
}

class _ClientShellState extends State<ClientShell> {
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
    await SessionStorage().clear();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const RoleSelectScreen()));
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
        title: Text(l10n.cshMyBookletTitle, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 17, letterSpacing: 0.3)),
        actions: [
          Tooltip(
            message: _online ? l10n.hsStatusActive : l10n.asOfflineTooltip,
            child: Padding(
              padding: const EdgeInsets.only(right: 4),
              child: Icon(_online ? Icons.wifi : Icons.wifi_off, color: _online ? MicrofiColors.secondaryFixed : MicrofiColors.tertiaryFixedDim),
            ),
          ),
          PopupMenuButton<String>(
            icon: const Icon(Icons.account_circle_outlined),
            onSelected: (value) {
              if (value == 'signout') _signOut();
            },
            itemBuilder: (context) => [
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
          destinations: List.generate(tabs.length, (i) => NavigationDestination(icon: Icon(_icons[i]), label: tabs[i])),
        ),
      ),
    );
  }

  Widget _buildTab() {
    switch (_selectedIndex) {
      case 1:
        return ClientHistoryScreen(key: UniqueKey(), token: widget.token);
      case 2:
        return ClientWalletScreen(key: UniqueKey(), token: widget.token);
      case 0:
      default:
        return ClientHomeScreen(key: UniqueKey(), token: widget.token);
    }
  }
}
