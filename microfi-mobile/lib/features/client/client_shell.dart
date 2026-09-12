import 'dart:async';

import 'package:flutter/material.dart';
import '../../core/connectivity_service.dart';
import '../../core/design_tokens.dart';
import '../../core/session_storage.dart';
import '../auth/role_select_screen.dart';
import 'client_history_screen.dart';
import 'client_home_screen.dart';
import 'client_models.dart';
import 'client_notification_history_screen.dart';
import 'client_report_agent_screen.dart';
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
    await SessionStorage().clear();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const RoleSelectScreen()));
  }

  void _openNotifications() {
    final l10n = AppLocalizations.of(context)!;
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(title: Text(l10n.nhTitle)),
        body: SafeArea(child: ClientNotificationHistoryScreen(token: widget.token)),
      ),
    ));
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
        elevation: 8,
        shadowColor: Colors.black.withValues(alpha: 0.25),
        surfaceTintColor: Colors.transparent,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.only(bottomLeft: Radius.circular(MicrofiRadius.lg), bottomRight: Radius.circular(MicrofiRadius.lg)),
        ),
        automaticallyImplyLeading: false,
        titleSpacing: 20,
        title: Text(l10n.cshMyBookletTitle, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 17, letterSpacing: 0.3)),
        actions: [
          Tooltip(
            message: l10n.asNotificationsTooltip,
            child: InkWell(
              customBorder: const CircleBorder(),
              onTap: _openNotifications,
              child: Container(
                width: 32,
                height: 32,
                margin: const EdgeInsets.only(right: 4),
                alignment: Alignment.center,
                decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.12), shape: BoxShape.circle),
                child: const Icon(Icons.notifications_outlined, size: 18, color: Colors.white),
              ),
            ),
          ),
          Tooltip(
            message: _online ? l10n.hsStatusActive : l10n.asOfflineTooltip,
            child: Container(
              width: 32,
              height: 32,
              margin: const EdgeInsets.only(right: 4),
              alignment: Alignment.center,
              decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.12), shape: BoxShape.circle),
              child: Icon(_online ? Icons.wifi_rounded : Icons.wifi_off_rounded, size: 18, color: _online ? MicrofiColors.secondaryFixed : MicrofiColors.tertiaryFixedDim),
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
              if (value == 'signout') {
                _signOut();
              } else if (value == 'report_agent') {
                Navigator.of(context).push(MaterialPageRoute(builder: (_) => ClientReportAgentScreen(token: widget.token)));
              }
            },
            itemBuilder: (context) => [
              PopupMenuItem(
                value: 'report_agent',
                child: Row(children: [const Icon(Icons.shield_outlined, size: 20, color: MicrofiColors.primary), const SizedBox(width: 10), Text(l10n.cshReportAgentMenuItem)]),
              ),
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
            destinations: List.generate(tabs.length, (i) => NavigationDestination(icon: Icon(_icons[i]), label: tabs[i])),
          ),
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
