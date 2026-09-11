import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_history_screen.dart';
import 'client_models.dart';
import 'client_receipt_scan_screen.dart';
import 'client_repository.dart';
import '../../l10n/app_localizations.dart';

/// Graphical Design/client/client_digital_booklet_home — "My Booklet": identity + status and
/// recent contributions. Balance is deliberately not shown here — it isn't backed by a real CBS
/// yet, see client_wallet_screen.dart's own balance display for the same caveat. "Request
/// Withdrawal" has no backend behind it yet either, so it isn't offered here as a working action.
class ClientHomeScreen extends StatefulWidget {
  final String token;

  const ClientHomeScreen({super.key, required this.token});

  @override
  State<ClientHomeScreen> createState() => _ClientHomeScreenState();
}

class _ClientHomeScreenState extends State<ClientHomeScreen> {
  late final ClientSelfRepository _repository = ClientSelfRepository(widget.token);

  ClientSelfProfile? _profile;
  List<ClientHistoryEntry> _recent = [];
  List<ClientRecentCollection> _recentCollections = [];
  String? _error;
  bool _loading = true;
  bool _confirmingPayment = false;

  ClientBroadcastMessage? _broadcast;
  String? _dismissedBroadcastId;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await Future.wait([
        _repository.fetchProfile(),
        _repository.fetchHistory(),
        _repository.fetchRecentCollections(),
      ]);
      if (!mounted) return;
      setState(() {
        _profile = results[0] as ClientSelfProfile;
        _recent = (results[1] as List<ClientHistoryEntry>).take(10).toList();
        _recentCollections = (results[2] as List<ClientRecentCollection>).take(10).toList();
      });
      _checkBroadcasts();
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // ADMIN/BRANCH_MANAGER announcement — no push infrastructure in this app (SMS carries the
  // urgency), so this is refreshed on load/pull-to-refresh rather than a background poll like the
  // agent app's Home screen, since the client app isn't left open in the background the same way.
  Future<void> _checkBroadcasts() async {
    try {
      final broadcasts = await _repository.fetchBroadcasts();
      if (!mounted || broadcasts.isEmpty) return;
      final latest = broadcasts.first;
      if (latest.id == _dismissedBroadcastId) return;
      setState(() => _broadcast = latest);
    } catch (_) {
      // Best-effort — silently retried on the next load/pull-to-refresh.
    }
  }

  void _dismissBroadcast() {
    final broadcast = _broadcast;
    if (broadcast == null) return;
    setState(() {
      _dismissedBroadcastId = broadcast.id;
      _broadcast = null;
    });
  }

  Future<void> _confirmPayment() async {
    final l10n = AppLocalizations.of(context)!;
    final pin = await promptForPin(
      context,
      message: l10n.chEnterPinConfirmPayment,
    );
    if (pin == null || pin.isEmpty) return;
    if (!mounted) return;

    setState(() => _confirmingPayment = true);
    try {
      final result = await _repository.confirmActivationPayment(pin);
      if (!mounted) return;
      final active = result.status == 'ACTIVE';
      await showSuccessDialog(
        context,
        active ? l10n.chBookletActiveMessage : l10n.chPaymentConfirmedWaitingMessage,
        title: active ? l10n.chBookletActivatedTitle : l10n.chPaymentConfirmedTitle,
      );
      if (!mounted) return;
      await _load();
    } catch (e) {
      if (!mounted) return;
      await showErrorDialog(context, e, title: l10n.chCouldNotConfirmPaymentTitle);
    } finally {
      if (mounted) setState(() => _confirmingPayment = false);
    }
  }

  void _openHistory() {
    final l10n = AppLocalizations.of(context)!;
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(title: Text(l10n.chContributionHistoryTitle)),
        body: SafeArea(child: ClientHistoryScreen(token: widget.token)),
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loading && _profile == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null && _profile == null) {
      return ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const SizedBox(height: 60),
          const Icon(Icons.error_outline, color: MicrofiColors.error, size: 34),
          const SizedBox(height: 10),
          Text(_error!, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: MicrofiColors.error)),
          const SizedBox(height: 14),
          Center(child: FilledButton(onPressed: _load, child: Text(l10n.commonRetry))),
        ],
      );
    }

    final profile = _profile!;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(MicrofiSpacing.page),
        children: [
          Container(
            padding: const EdgeInsets.fromLTRB(18, 18, 18, 16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(MicrofiRadius.lg),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [MicrofiColors.primary, MicrofiColors.primaryContainer],
              ),
              boxShadow: MicrofiShadows.soft,
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 46,
                  height: 46,
                  alignment: Alignment.center,
                  decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                  child: const Icon(Icons.menu_book_rounded, color: MicrofiColors.primary, size: 22),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(profile.fullName, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: Colors.white)),
                      const SizedBox(height: 1),
                      Text(profile.mfiMemberNo, style: TextStyle(fontSize: 12, color: Colors.white.withValues(alpha: 0.75))),
                      const SizedBox(height: 3),
                      // Confirms which MFI this session belongs to — MICROFI serves several MFIs,
                      // each its own separate deployment, so this is the client's own signal that
                      // they're using the right institution's app.
                      Text(profile.mfiName, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: MicrofiColors.secondaryFixed)),
                    ],
                  ),
                ),
                _TokenStatusPill(status: profile.tokenStatus),
              ],
            ),
          ),
          if (_broadcast != null) ...[
            const SizedBox(height: MicrofiSpacing.gapLg),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: MicrofiColors.primaryContainer,
                borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                boxShadow: MicrofiShadows.soft,
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 28,
                    height: 28,
                    decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.14), shape: BoxShape.circle),
                    child: const Icon(Icons.campaign_outlined, size: 15, color: Colors.white),
                  ),
                  const SizedBox(width: 10),
                  Expanded(child: Text(_broadcast!.message, style: const TextStyle(fontSize: 12.5, color: Colors.white))),
                  InkWell(
                    onTap: _dismissBroadcast,
                    borderRadius: BorderRadius.circular(MicrofiRadius.full),
                    child: const Padding(
                      padding: EdgeInsets.all(2),
                      child: Icon(Icons.close_rounded, color: Colors.white, size: 16),
                    ),
                  ),
                ],
              ),
            ),
          ],
          if (profile.tokenStatus != 'ACTIVE') ...[
            const SizedBox(height: MicrofiSpacing.gapLg),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: MicrofiColors.tertiaryFixed,
                borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                boxShadow: MicrofiShadows.soft,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.35), shape: BoxShape.circle),
                        child: const Icon(Icons.hourglass_top_rounded, size: 16, color: MicrofiColors.onTertiaryFixedVariant),
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          profile.tokenStatus == 'EXPIRED' ? l10n.chRenewalNeeded : l10n.chActivationPending,
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.onTertiaryFixedVariant),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    l10n.chConfirmOncePaidMessage,
                    style: const TextStyle(fontSize: 11, color: MicrofiColors.onTertiaryFixedVariant),
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: _confirmingPayment ? null : _confirmPayment,
                      child: _confirmingPayment
                          ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : Text(l10n.chConfirmActivationPaymentButton, style: const TextStyle(fontSize: 13)),
                    ),
                  ),
                ],
              ),
            ),
          ],
          if (_recentCollections.isNotEmpty) ...[
            const SizedBox(height: MicrofiSpacing.gapLg),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: MicrofiColors.tertiaryFixed,
                borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                boxShadow: MicrofiShadows.soft,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.35), shape: BoxShape.circle),
                        child: const Icon(Icons.schedule_rounded, size: 16, color: MicrofiColors.onTertiaryFixedVariant),
                      ),
                      const SizedBox(width: 8),
                      Text(l10n.chJustCollected, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.onTertiaryFixedVariant)),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    l10n.chRecordedReflectedMessage,
                    style: const TextStyle(fontSize: 11, color: MicrofiColors.onTertiaryFixedVariant),
                  ),
                  const SizedBox(height: 8),
                  ..._recentCollections.map((c) => Padding(
                        padding: const EdgeInsets.symmetric(vertical: 3),
                        child: Row(
                          children: [
                            Expanded(
                              child: Text(
                                c.locationName ?? _fmtDate(c.collectedAt),
                                style: const TextStyle(fontSize: 12, color: MicrofiColors.onTertiaryFixedVariant),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Text(l10n.hsAmountCollectedPlus(_fmt(c.amountXaf)), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: MicrofiColors.onTertiaryFixedVariant)),
                          ],
                        ),
                      )),
                ],
              ),
            ),
          ],
          const SizedBox(height: MicrofiSpacing.gapLg),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ClientReceiptScanScreen())),
              icon: const Icon(Icons.qr_code_scanner_rounded, size: 18),
              label: Text(l10n.chScanReceiptFromAgent, style: const TextStyle(fontSize: 13)),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(46)),
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gap),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: () => showDialog<void>(
                context: context,
                builder: (_) => AlertDialog(
                  title: Text(l10n.chHowToTopUpTitle),
                  content: Text(l10n.chHowToTopUpMessage),
                  actions: [FilledButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.commonOk))],
                ),
              ),
              icon: const Icon(Icons.info_outline_rounded, size: 16),
              label: Text(l10n.chTopUpInfo, style: const TextStyle(fontSize: 13)),
              style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(46)),
            ),
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(l10n.chRecentContributions, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
              TextButton(onPressed: _openHistory, child: Text(l10n.chViewAll, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600))),
            ],
          ),
          const SizedBox(height: 4),
          Container(
            decoration: BoxDecoration(
              color: MicrofiColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(MicrofiRadius.lg),
              boxShadow: MicrofiShadows.soft,
            ),
            padding: const EdgeInsets.symmetric(horizontal: 6),
            child: _recent.isEmpty
                ? Padding(
                    padding: const EdgeInsets.symmetric(vertical: 28),
                    child: Center(
                      child: Text(l10n.chNoContributionsRecorded, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
                    ),
                  )
                : Column(
                    children: [
                      for (int i = 0; i < _recent.length; i++) ...[
                        _ContributionRow(entry: _recent[i]),
                        if (i < _recent.length - 1) Divider(height: 1, indent: 58, color: MicrofiColors.outlineVariant.withValues(alpha: 0.5)),
                      ],
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  String _fmtDate(DateTime d) {
    final local = d.toLocal();
    return '${local.day}/${local.month}/${local.year}';
  }

  String _fmt(int value) {
    final s = value.toString();
    final buffer = StringBuffer();
    for (int i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buffer.write(',');
      buffer.write(s[i]);
    }
    return buffer.toString();
  }
}

class _ContributionRow extends StatelessWidget {
  final ClientHistoryEntry entry;

  const _ContributionRow({required this.entry});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final local = entry.date.toLocal();
    final date = '${local.day}/${local.month} ${TimeOfDay.fromDateTime(local).format(context)}';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 6),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: const BoxDecoration(color: MicrofiColors.secondaryContainer, shape: BoxShape.circle),
            child: const Icon(Icons.arrow_downward_rounded, color: MicrofiColors.onSecondaryContainer, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(entry.type, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                Text(date, style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
              ],
            ),
          ),
          Text(l10n.hsAmountCollectedPlus(_fmt(entry.amountXaf)), style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: MicrofiColors.secondary)),
        ],
      ),
    );
  }

  String _fmt(int value) {
    final s = value.toString();
    final buffer = StringBuffer();
    for (int i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) buffer.write(',');
      buffer.write(s[i]);
    }
    return buffer.toString();
  }
}

class _TokenStatusPill extends StatelessWidget {
  final String status;

  const _TokenStatusPill({required this.status});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final active = status == 'ACTIVE';
    final dotColor = active ? MicrofiColors.secondaryFixed : (status == 'EXPIRED' ? MicrofiColors.errorContainer : Colors.white70);
    final label = active ? l10n.chStatusActive : (status == 'EXPIRED' ? l10n.chStatusExpired : l10n.chStatusNotActivated);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(MicrofiRadius.full),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 6, height: 6, decoration: BoxDecoration(color: dotColor, shape: BoxShape.circle)),
          const SizedBox(width: 5),
          Text(label, style: const TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, color: Colors.white)),
        ],
      ),
    );
  }
}
