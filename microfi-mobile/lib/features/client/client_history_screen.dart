import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_models.dart';
import 'client_repository.dart';

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
      setState(() => _error = friendlyErrorMessage(e));
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
          Center(child: FilledButton(onPressed: _load, child: const Text('Retry'))),
        ],
      );
    }

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(MicrofiSpacing.page),
        children: [
          const Text('Contribution History', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: MicrofiColors.primary)),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(MicrofiSpacing.card),
            decoration: BoxDecoration(
              color: MicrofiColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(MicrofiRadius.md),
              border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Total Contributions This Month', style: TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
                const SizedBox(height: 4),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Text(_fmt(_thisMonthTotal), style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                    const SizedBox(width: 5),
                    const Text('XAF', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant)),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          if (_entries.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 40),
              child: Column(
                children: [
                  Icon(Icons.receipt_long, color: MicrofiColors.outlineVariant, size: 40),
                  SizedBox(height: 10),
                  Text('No contributions recorded yet.', style: TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
                ],
              ),
            )
          else
            ..._entries.map((e) {
              final local = e.date.toLocal();
              final date = '${local.day}/${local.month}/${local.year}';
              final time = TimeOfDay.fromDateTime(local).format(context);
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
                          Text('${_fmt(e.amountXaf)} XAF', style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                          Text('$date, $time • ${e.reference}', style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                        ],
                      ),
                    ),
                    const Icon(Icons.check_circle, color: MicrofiColors.secondary, size: 18),
                  ],
                ),
              );
            }),
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
