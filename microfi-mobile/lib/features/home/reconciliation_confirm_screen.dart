import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../collection/collection_repository.dart';
import 'reconciliation_repository.dart';
import '../../l10n/app_localizations.dart';

/// UC pending: the agent's own sign-off on a cashier's physical count — until they confirm (or it
/// auto-expires server-side), the cash counted still occupies their escrow ceiling. See
/// CollectionReconciliationStatus's doc on the backend for the full reasoning.
class ReconciliationConfirmScreen extends StatefulWidget {
  final String token;

  const ReconciliationConfirmScreen({super.key, required this.token});

  @override
  State<ReconciliationConfirmScreen> createState() => _ReconciliationConfirmScreenState();
}

class _ReconciliationConfirmScreenState extends State<ReconciliationConfirmScreen> {
  late final ReconciliationRepository _repository = ReconciliationRepository(widget.token);

  List<PendingReconciliationLine> _lines = [];
  bool _loading = true;
  String? _error;
  String? _confirmingLineId;

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
      final lines = await _repository.listMyPendingConfirmations();
      if (!mounted) return;
      setState(() => _lines = lines);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _confirm(PendingReconciliationLine line) async {
    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(l10n.rcConfirmDialogTitle),
        content: Text(l10n.rcConfirmDialogMessage(_fmt(line.totalXaf), line.collectionCount)),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(l10n.commonCancel)),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: Text(l10n.rcConfirmButton)),
        ],
      ),
    );
    if (confirmed != true) return;

    setState(() => _confirmingLineId = line.lineId);
    try {
      await _repository.confirm(line.lineId);
      if (!mounted) return;
      await showSuccessDialog(context, l10n.rcConfirmSuccess);
      _load();
    } catch (e) {
      if (!mounted) return;
      await showErrorDialog(context, e, title: l10n.rcConfirmFailed);
    } finally {
      if (mounted) setState(() => _confirmingLineId = null);
    }
  }

  void _review(PendingReconciliationLine line) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => ReconciliationLineCollectionsScreen(token: widget.token, line: line),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.rcTitle)),
      body: SafeArea(child: _buildBody(l10n)),
    );
  }

  Widget _buildBody(AppLocalizations l10n) {
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
          Text(l10n.rcSubtitle, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
          const SizedBox(height: MicrofiSpacing.gapLg),
          if (_lines.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 40),
              child: Column(
                children: [
                  const Icon(Icons.check_circle_outline, color: MicrofiColors.outlineVariant, size: 40),
                  const SizedBox(height: 10),
                  Text(l10n.rcEmptyState, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
                ],
              ),
            )
          else
            ..._lines.map((line) => Padding(
                  padding: const EdgeInsets.only(bottom: MicrofiSpacing.gap),
                  child: Container(
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
                        Text(l10n.rcLineSummary(line.collectionCount, _fmt(line.totalXaf)),
                            style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                        if (line.lastCountedAt != null) ...[
                          const SizedBox(height: 4),
                          Text(
                            l10n.rcCountedAt(_date(line.lastCountedAt!), _time(context, line.lastCountedAt!)),
                            style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant),
                          ),
                        ],
                        const SizedBox(height: MicrofiSpacing.gap),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton(onPressed: () => _review(line), child: Text(l10n.rcReviewButton)),
                            ),
                            const SizedBox(width: MicrofiSpacing.gap),
                            Expanded(
                              child: FilledButton(
                                onPressed: _confirmingLineId == line.lineId ? null : () => _confirm(line),
                                child: _confirmingLineId == line.lineId
                                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                                    : Text(l10n.rcConfirmButton),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                )),
        ],
      ),
    );
  }

  String _date(DateTime dt) {
    final local = dt.toLocal();
    return '${local.day}/${local.month}/${local.year}';
  }

  String _time(BuildContext context, DateTime dt) => TimeOfDay.fromDateTime(dt.toLocal()).format(context);

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

/// One reconciliation line's individual collections — lets the agent see exactly what's in it
/// before confirming, or pick one to request rejection on.
class ReconciliationLineCollectionsScreen extends StatefulWidget {
  final String token;
  final PendingReconciliationLine line;

  const ReconciliationLineCollectionsScreen({super.key, required this.token, required this.line});

  @override
  State<ReconciliationLineCollectionsScreen> createState() => _ReconciliationLineCollectionsScreenState();
}

class _ReconciliationLineCollectionsScreenState extends State<ReconciliationLineCollectionsScreen> {
  late final CollectionRepository _collectionRepository = CollectionRepository(widget.token);
  late final ReconciliationRepository _reconciliationRepository = ReconciliationRepository(widget.token);

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
      final collections = await _collectionRepository.listForReconciliationLine(widget.line.lineId);
      if (!mounted) return;
      setState(() => _collections = collections);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _requestRejection(CollectionSummary collection) async {
    final l10n = AppLocalizations.of(context)!;
    final controller = TextEditingController();
    final reason = await showDialog<String>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(l10n.rcRequestRejectionButton),
        content: TextField(
          controller: controller,
          maxLines: 3,
          decoration: InputDecoration(labelText: l10n.rcRejectionReasonLabel, border: const OutlineInputBorder()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(l10n.commonCancel)),
          FilledButton(
            onPressed: () {
              if (controller.text.trim().isEmpty) return;
              Navigator.of(context).pop(controller.text.trim());
            },
            child: Text(l10n.rcRejectionSubmit),
          ),
        ],
      ),
    );
    if (reason == null || reason.isEmpty) return;

    try {
      await _reconciliationRepository.requestCollectionRejection(collection.id, reason);
      if (!mounted) return;
      await showSuccessDialog(context, l10n.rcRejectionSuccess);
    } catch (e) {
      if (!mounted) return;
      await showErrorDialog(context, e, title: l10n.rcRejectionFailed);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.rcLineCollectionsTitle)),
      body: SafeArea(child: _buildBody(l10n)),
    );
  }

  Widget _buildBody(AppLocalizations l10n) {
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

    return ListView(
      padding: const EdgeInsets.all(MicrofiSpacing.page),
      children: _collections.map((c) {
        final time = TimeOfDay.fromDateTime(c.collectedAt.toLocal()).format(context);
        final date = '${c.collectedAt.toLocal().day}/${c.collectedAt.toLocal().month}/${c.collectedAt.toLocal().year}';
        return Padding(
          padding: const EdgeInsets.only(bottom: MicrofiSpacing.gap),
          child: Container(
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
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Text(c.clientName ?? '', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
                    ),
                    Text('${c.amountXaf} XAF', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.secondary)),
                  ],
                ),
                Text('$date • $time', style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                const SizedBox(height: MicrofiSpacing.gap),
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(onPressed: () => _requestRejection(c), child: Text(l10n.rcRequestRejectionButton)),
                ),
              ],
            ),
          ),
        );
      }).toList(),
    );
  }
}
