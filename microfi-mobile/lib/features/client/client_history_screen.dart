import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_models.dart';
import 'client_repository.dart';
import '../../l10n/app_localizations.dart';

/// UC-22 — full contribution history / mini-statement, replacing the paper booklet page.
class ClientHistoryScreen extends StatefulWidget {
  final String token;

  const ClientHistoryScreen({super.key, required this.token});

  @override
  State<ClientHistoryScreen> createState() => _ClientHistoryScreenState();
}

class _ClientHistoryScreenState extends State<ClientHistoryScreen> {
  late final ClientSelfRepository _repository = ClientSelfRepository(widget.token);

  List<ClientHistoryEntry> _entries = [];
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
      final results = await _repository.fetchHistory();
      if (!mounted) return;
      setState(() => _entries = results);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  int get _thisMonthTotal {
    final now = DateTime.now();
    return _entries
        .where((e) => e.date.toLocal().year == now.year && e.date.toLocal().month == now.month)
        .fold(0, (sum, e) => sum + e.amountXaf);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const SizedBox(height: 50),
          const Icon(Icons.error_outline, color: MicrofiColors.error, size: 34),
          const SizedBox(height: 10),
          Text(_error!, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: MicrofiColors.error)),
          const SizedBox(height: 14),
          Center(child: FilledButton(onPressed: _load, child: Text(l10n.commonRetry))),
        ],
      );
    }

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(MicrofiSpacing.page),
        children: [
          Text(l10n.chContributionHistoryTitle, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: MicrofiColors.primary)),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(MicrofiRadius.lg),
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [MicrofiColors.secondary, Color(0xFF00483C)],
              ),
              boxShadow: MicrofiShadows.soft,
            ),
            child: Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.16), shape: BoxShape.circle),
                  child: const Icon(Icons.calendar_month_rounded, color: Colors.white, size: 22),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l10n.chTotalContributionsThisMonth, style: TextStyle(fontSize: 12, color: Colors.white.withValues(alpha: 0.8), fontWeight: FontWeight.w600)),
                      const SizedBox(height: 3),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.baseline,
                        textBaseline: TextBaseline.alphabetic,
                        children: [
                          Text(_fmt(_thisMonthTotal), style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w800, color: Colors.white)),
                          const SizedBox(width: 5),
                          Text('XAF', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.white.withValues(alpha: 0.85))),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          if (_entries.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 40),
              child: Column(
                children: [
                  Icon(Icons.receipt_long_rounded, color: MicrofiColors.outline.withValues(alpha: 0.6), size: 40),
                  const SizedBox(height: 10),
                  Text(l10n.chNoContributionsRecorded, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
                ],
              ),
            )
          else
            Container(
              decoration: BoxDecoration(
                color: MicrofiColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                boxShadow: MicrofiShadows.soft,
              ),
              padding: const EdgeInsets.symmetric(horizontal: 6),
              child: Column(
                children: [
                  for (int i = 0; i < _entries.length; i++) ...[
                    Builder(builder: (context) {
                      final e = _entries[i];
                      final local = e.date.toLocal();
                      final date = '${local.day}/${local.month}/${local.year}';
                      final time = TimeOfDay.fromDateTime(local).format(context);
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
                                  Text(l10n.amountXaf(_fmt(e.amountXaf)), style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w800, color: MicrofiColors.primary)),
                                  Text(l10n.chDateTimeReferenceLine(date, time, e.reference), style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                                ],
                              ),
                            ),
                            const Icon(Icons.check_circle_rounded, color: MicrofiColors.secondary, size: 20),
                          ],
                        ),
                      );
                    }),
                    if (i < _entries.length - 1) Divider(height: 1, indent: 58, color: MicrofiColors.outlineVariant.withValues(alpha: 0.5)),
                  ],
                ],
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
