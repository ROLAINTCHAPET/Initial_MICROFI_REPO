import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../collection/collection_repository.dart';
import '../../l10n/app_localizations.dart';

/// Full collection history — same list as Home's "Recent Collections" but unbounded (up to the
/// server's top-50 cap; see CollectionController#myCollections).
class HistoryScreen extends StatefulWidget {
  final String token;

  const HistoryScreen({super.key, required this.token});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  late final CollectionRepository _repository = CollectionRepository(widget.token);

  List<CollectionSummary> _collections = [];
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
      final results = await _repository.listMine();
      if (!mounted) return;
      setState(() => _collections = results);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  int get _thisMonthTotal {
    final now = DateTime.now();
    return _collections
        .where((c) => c.collectedAt.toLocal().year == now.year && c.collectedAt.toLocal().month == now.month)
        .fold(0, (sum, c) => sum + c.amountXaf);
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
          Text(l10n.hsCollectionHistoryTitle, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: MicrofiColors.primary)),
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
                      Text(l10n.histTotalCollectedThisMonth, style: TextStyle(fontSize: 12, color: Colors.white.withValues(alpha: 0.8), fontWeight: FontWeight.w600)),
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
          if (_collections.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 40),
              child: Column(
                children: [
                  Icon(Icons.receipt_long_rounded, color: MicrofiColors.outline.withValues(alpha: 0.6), size: 40),
                  const SizedBox(height: 10),
                  Text(l10n.hsNoCollectionsRecorded, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
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
                  for (int i = 0; i < _collections.length; i++) ...[
                    Builder(builder: (context) {
                      final c = _collections[i];
                      final time = TimeOfDay.fromDateTime(c.collectedAt.toLocal()).format(context);
                      final date = '${c.collectedAt.toLocal().day}/${c.collectedAt.toLocal().month}/${c.collectedAt.toLocal().year}';
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
                                  Text(c.clientName ?? l10n.hsUnknownClient, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                                  Text(l10n.histDateTimeCashLine(date, time), style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                                  if (c.locationName != null)
                                    Text(c.locationName!, style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant), maxLines: 1, overflow: TextOverflow.ellipsis),
                                ],
                              ),
                            ),
                            Text(l10n.hsAmountCollectedPlus(_fmt(c.amountXaf)), style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: MicrofiColors.secondary)),
                          ],
                        ),
                      );
                    }),
                    if (i < _collections.length - 1) Divider(height: 1, indent: 58, color: MicrofiColors.outlineVariant.withValues(alpha: 0.5)),
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
