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
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
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
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(profile.fullName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: MicrofiColors.primary)),
                    const SizedBox(height: 1),
                    Text(profile.mfiMemberNo, style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
                    const SizedBox(height: 2),
                    // Confirms which MFI this session belongs to — MICROFI serves several MFIs,
                    // each its own separate deployment, so this is the client's own signal that
                    // they're using the right institution's app.
                    Text(profile.mfiName, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: MicrofiColors.secondary)),
                  ],
                ),
              ),
              _TokenStatusPill(status: profile.tokenStatus),
            ],
          ),
          if (profile.tokenStatus != 'ACTIVE') ...[
            const SizedBox(height: MicrofiSpacing.gapLg),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(MicrofiSpacing.card),
              decoration: BoxDecoration(
                color: MicrofiColors.tertiaryFixed.withValues(alpha: 0.25),
                borderRadius: BorderRadius.circular(MicrofiRadius.md),
                border: Border.all(color: MicrofiColors.tertiaryFixedDim, width: MicrofiBorders.width),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.hourglass_top, size: 16, color: MicrofiColors.onTertiaryFixedVariant),
                      const SizedBox(width: 6),
                      Text(
                        profile.tokenStatus == 'EXPIRED' ? l10n.chRenewalNeeded : l10n.chActivationPending,
                        style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.onTertiaryFixedVariant),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  Text(
                    l10n.chConfirmOncePaidMessage,
                    style: const TextStyle(fontSize: 11, color: MicrofiColors.onTertiaryFixedVariant),
                  ),
                  const SizedBox(height: 10),
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
              padding: const EdgeInsets.all(MicrofiSpacing.card),
              decoration: BoxDecoration(
                color: MicrofiColors.tertiaryFixed.withValues(alpha: 0.25),
                borderRadius: BorderRadius.circular(MicrofiRadius.md),
                border: Border.all(color: MicrofiColors.tertiaryFixedDim, width: MicrofiBorders.width),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.schedule, size: 16, color: MicrofiColors.onTertiaryFixedVariant),
                      const SizedBox(width: 6),
                      Text(l10n.chJustCollected, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.onTertiaryFixedVariant)),
                    ],
                  ),
                  const SizedBox(height: 2),
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
              icon: const Icon(Icons.qr_code_scanner, size: 18),
              label: Text(l10n.chScanReceiptFromAgent, style: const TextStyle(fontSize: 13)),
              style: FilledButton.styleFrom(
                minimumSize: const Size.fromHeight(42),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
              ),
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
              icon: const Icon(Icons.info_outline, size: 16),
              label: Text(l10n.chTopUpInfo, style: const TextStyle(fontSize: 13)),
              style: OutlinedButton.styleFrom(
                foregroundColor: MicrofiColors.primary,
                side: const BorderSide(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
                minimumSize: const Size.fromHeight(42),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(l10n.chRecentContributions, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
              TextButton(onPressed: _openHistory, child: Text(l10n.chViewAll, style: const TextStyle(fontSize: 13))),
            ],
          ),
          const Divider(color: MicrofiColors.outlineVariant, height: 1),
          if (_recent.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 20),
              child: Text(l10n.chNoContributionsRecorded, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
            )
          else
            ..._recent.map((e) => _ContributionRow(entry: e)),
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
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: const BoxDecoration(color: MicrofiColors.secondaryContainer, shape: BoxShape.circle),
            child: const Icon(Icons.arrow_downward, color: MicrofiColors.onSecondaryContainer, size: 16),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(entry.type, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
                Text(date, style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
              ],
            ),
          ),
          Text(l10n.hsAmountCollectedPlus(_fmt(entry.amountXaf)), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.secondary)),
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
    final color = active ? MicrofiColors.secondary : (status == 'EXPIRED' ? MicrofiColors.error : MicrofiColors.outline);
    final label = active ? l10n.chStatusActive : (status == 'EXPIRED' ? l10n.chStatusExpired : l10n.chStatusNotActivated);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(MicrofiRadius.full),
        border: Border.all(color: color),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 6, height: 6, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
          const SizedBox(width: 5),
          Text(label, style: TextStyle(fontSize: 10, fontWeight: FontWeight.w700, color: color)),
        ],
      ),
    );
  }
}
