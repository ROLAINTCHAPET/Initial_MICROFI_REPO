import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_history_screen.dart';
import 'client_models.dart';
import 'client_repository.dart';

/// Graphical Design/client/client_digital_booklet_home — "My Booklet": identity + status,
/// current balance, and recent contributions. All data is real (UC-20/21/22); "Request
/// Withdrawal" has no backend behind it yet (see client_wallet_screen.dart), so it isn't offered
/// here as a working action.
class ClientHomeScreen extends StatefulWidget {
  final String token;

  const ClientHomeScreen({super.key, required this.token});

  @override
  State<ClientHomeScreen> createState() => _ClientHomeScreenState();
}

class _ClientHomeScreenState extends State<ClientHomeScreen> {
  late final ClientSelfRepository _repository = ClientSelfRepository(widget.token);

  ClientSelfProfile? _profile;
  ClientBalance? _balance;
  List<ClientHistoryEntry> _recent = [];
  List<ClientRecentCollection> _recentCollections = [];
  String? _error;
  bool _loading = true;

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
        _repository.fetchBalance(),
        _repository.fetchHistory(),
        _repository.fetchRecentCollections(),
      ]);
      if (!mounted) return;
      setState(() {
        _profile = results[0] as ClientSelfProfile;
        _balance = results[1] as ClientBalance;
        _recent = (results[2] as List<ClientHistoryEntry>).take(5).toList();
        _recentCollections = (results[3] as List<ClientRecentCollection>).take(3).toList();
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _openHistory() {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(title: const Text('Contribution History')),
        body: SafeArea(child: ClientHistoryScreen(token: widget.token)),
      ),
    ));
  }

  @override
  Widget build(BuildContext context) {
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
          Center(child: FilledButton(onPressed: _load, child: const Text('Retry'))),
        ],
      );
    }

    final profile = _profile!;
    final balance = _balance;

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
                  ],
                ),
              ),
              _TokenStatusPill(status: profile.tokenStatus),
            ],
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(MicrofiSpacing.card + 2),
            decoration: BoxDecoration(
              color: MicrofiColors.primary,
              borderRadius: BorderRadius.circular(MicrofiRadius.md),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('CURRENT BALANCE', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: Colors.white70, letterSpacing: 0.5)),
                const SizedBox(height: 6),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Text(balance != null ? _fmt(balance.balanceXaf) : '—', style: const TextStyle(color: Colors.white, fontSize: 26, fontWeight: FontWeight.w700)),
                    const SizedBox(width: 6),
                    const Text('XAF', style: TextStyle(color: Colors.white70, fontSize: 14, fontWeight: FontWeight.w600)),
                  ],
                ),
                if (balance != null) ...[
                  const SizedBox(height: 6),
                  Text('As of ${_fmtDate(balance.asOf)}', style: const TextStyle(color: Colors.white70, fontSize: 11)),
                ],
              ],
            ),
          ),
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
                      const Text('Just Collected', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.onTertiaryFixedVariant)),
                    ],
                  ),
                  const SizedBox(height: 2),
                  const Text(
                    'Recorded by MICROFI — reflected in your official balance at end of day.',
                    style: TextStyle(fontSize: 11, color: MicrofiColors.onTertiaryFixedVariant),
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
                            Text('+${_fmt(c.amountXaf)} XAF', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: MicrofiColors.onTertiaryFixedVariant)),
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
            child: OutlinedButton.icon(
              onPressed: () => showDialog<void>(
                context: context,
                builder: (_) => AlertDialog(
                  title: const Text('How to Top Up'),
                  content: const Text('Hand cash to a field agent visiting you, or visit your branch directly. Every deposit appears here once recorded.'),
                  actions: [FilledButton(onPressed: () => Navigator.of(context).pop(), child: const Text('OK'))],
                ),
              ),
              icon: const Icon(Icons.info_outline, size: 16),
              label: const Text('Top-up Info', style: TextStyle(fontSize: 13)),
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
              const Text('Recent Contributions', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
              TextButton(onPressed: _openHistory, child: const Text('View All', style: TextStyle(fontSize: 13))),
            ],
          ),
          const Divider(color: MicrofiColors.outlineVariant, height: 1),
          if (_recent.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Text('No contributions recorded yet.', style: TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
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
          Text('+${_fmt(entry.amountXaf)} XAF', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.secondary)),
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
    final active = status == 'ACTIVE';
    final color = active ? MicrofiColors.secondary : (status == 'EXPIRED' ? MicrofiColors.error : MicrofiColors.outline);
    final label = active ? 'ACTIVE' : (status == 'EXPIRED' ? 'EXPIRED' : 'NOT ACTIVATED');
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
