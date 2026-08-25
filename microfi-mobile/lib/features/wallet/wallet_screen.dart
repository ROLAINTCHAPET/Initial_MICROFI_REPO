import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../home/agent_profile.dart';
import '../home/home_repository.dart';
import '../../l10n/app_localizations.dart';

/// The funded escrow wallet (top-up balance/ceiling, administered by a cashier — see
/// EscrowService.topUp) — distinct from Home's "Today's Collections" card, which tracks the
/// separate, fast-moving daily-ceiling-usage number (BR-03). This tab is the slow-moving,
/// administrative side of escrow.
class WalletScreen extends StatefulWidget {
  final String token;
  final String agentId;

  const WalletScreen({super.key, required this.token, required this.agentId});

  @override
  State<WalletScreen> createState() => _WalletScreenState();
}

class _WalletScreenState extends State<WalletScreen> {
  late final HomeRepository _repository = HomeRepository(widget.token);

  EscrowStatus? _escrow;
  bool _loading = true;
  String? _error;

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
      final escrow = await _repository.fetchEscrow(widget.agentId);
      if (!mounted) return;
      setState(() => _escrow = escrow);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null || _escrow == null) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 60),
          const Icon(Icons.error_outline, color: MicrofiColors.error, size: 40),
          const SizedBox(height: 12),
          Text(_error ?? l10n.wsWalletUnavailable, textAlign: TextAlign.center, style: const TextStyle(color: MicrofiColors.error)),
          const SizedBox(height: 16),
          Center(child: FilledButton(onPressed: _load, child: Text(l10n.commonRetry))),
        ],
      );
    }

    final escrow = _escrow!;
    final baseUtilization = escrow.baseCeilingXaf > 0 ? (escrow.balanceXaf / escrow.baseCeilingXaf).clamp(0, 1).toDouble() : 0.0;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(MicrofiSpacing.page),
        children: [
          Text(l10n.wsWalletTitle, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: MicrofiColors.primary)),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Container(
            padding: const EdgeInsets.all(MicrofiSpacing.card + 2),
            decoration: BoxDecoration(
              color: MicrofiColors.primary,
              borderRadius: BorderRadius.circular(MicrofiRadius.md),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.1), shape: BoxShape.circle),
                      child: const Icon(Icons.account_balance_wallet, color: Colors.white, size: 16),
                    ),
                    const SizedBox(width: 10),
                    Text(l10n.wsEscrowWalletBalance, style: const TextStyle(color: Colors.white70, fontSize: 13, fontWeight: FontWeight.w600)),
                  ],
                ),
                const SizedBox(height: 12),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Text(_fmt(escrow.balanceXaf), style: const TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w700)),
                    const SizedBox(width: 6),
                    const Text('XAF', style: TextStyle(color: Colors.white70, fontSize: 14, fontWeight: FontWeight.w600)),
                  ],
                ),
                const SizedBox(height: 12),
                ClipRRect(
                  borderRadius: BorderRadius.circular(MicrofiRadius.full),
                  child: LinearProgressIndicator(
                    value: baseUtilization,
                    minHeight: 8,
                    backgroundColor: Colors.white.withValues(alpha: 0.15),
                    valueColor: const AlwaysStoppedAnimation(MicrofiColors.secondaryFixed),
                  ),
                ),
                const SizedBox(height: 6),
                Text(l10n.wsBaseCeiling(_fmt(escrow.baseCeilingXaf)), style: const TextStyle(color: Colors.white70, fontSize: 11)),
              ],
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          _InfoCard(
            title: l10n.wsEffectiveCeilingTitle,
            rows: [
              _InfoRow(label: l10n.wsEffectiveCeilingToday, value: l10n.amountXaf(_fmt(escrow.effectiveCeilingXaf))),
              _InfoRow(label: l10n.wsTodaysCollectionsLabel, value: l10n.amountXaf(_fmt(escrow.cumulativeTodayXaf))),
              _InfoRow(label: l10n.wsRemainingToday, value: l10n.amountXaf(_fmt(escrow.remainingCeilingXaf))),
            ],
          ),
          if (escrow.activeOverrideReason != null) ...[
            const SizedBox(height: MicrofiSpacing.gapLg),
            Container(
              padding: const EdgeInsets.all(MicrofiSpacing.card),
              decoration: BoxDecoration(
                color: MicrofiColors.tertiaryFixed.withValues(alpha: 0.3),
                borderRadius: BorderRadius.circular(MicrofiRadius.md),
                border: Border.all(color: MicrofiColors.tertiaryFixedDim),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.info_outline, color: MicrofiColors.onTertiaryFixedVariant, size: 18),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(l10n.wsActiveWaiver, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: MicrofiColors.onTertiaryFixedVariant)),
                        const SizedBox(height: 2),
                        Text(escrow.activeOverrideReason!, style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ],
          const SizedBox(height: MicrofiSpacing.gapLg),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 2),
            child: Text(
              l10n.wsTopUpAdminNote,
              style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant),
            ),
          ),
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

class _InfoRow {
  final String label;
  final String value;
  _InfoRow({required this.label, required this.value});
}

class _InfoCard extends StatelessWidget {
  final String title;
  final List<_InfoRow> rows;

  const _InfoCard({required this.title, required this.rows});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(MicrofiSpacing.card),
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: MicrofiColors.primary)),
          const SizedBox(height: 8),
          for (final row in rows)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(row.label, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
                  Text(row.value, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
