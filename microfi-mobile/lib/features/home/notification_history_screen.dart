import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'branch_notice_repository.dart';
import 'broadcast_repository.dart';
import '../../l10n/app_localizations.dart';

enum _NoticeKind { branchNotice, broadcast }

class _NoticeItem {
  final String id;
  final String message;
  final DateTime createdAt;
  final _NoticeKind kind;

  _NoticeItem({required this.id, required this.message, required this.createdAt, required this.kind});
}

/// Agent-facing history of everything that has ever popped up on Home as a branch notice or a
/// Back-Office broadcast (UC-15) — Home itself only ever pops up today's latest one (see
/// HomeScreen._isToday), so this is the only place an agent can re-read something from a
/// previous day. Same RECENT_LIMIT=20-per-type cap as the server ("recent" endpoints, not full
/// history), merged and sorted newest-first here since the two are distinct backend resources.
class NotificationHistoryScreen extends StatefulWidget {
  final String token;

  const NotificationHistoryScreen({super.key, required this.token});

  @override
  State<NotificationHistoryScreen> createState() => _NotificationHistoryScreenState();
}

class _NotificationHistoryScreenState extends State<NotificationHistoryScreen> {
  List<_NoticeItem> _items = [];
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
      final results = await Future.wait([
        BranchNoticeRepository(widget.token).listMine(),
        BroadcastRepository(widget.token).listMine(),
      ]);
      final notices = results[0] as List<BranchNotice>;
      final broadcasts = results[1] as List<BroadcastMessage>;
      final merged = [
        ...notices.map((n) => _NoticeItem(id: n.id, message: n.message, createdAt: n.createdAt, kind: _NoticeKind.branchNotice)),
        ...broadcasts.map((b) => _NoticeItem(id: b.id, message: b.message, createdAt: b.createdAt, kind: _NoticeKind.broadcast)),
      ]..sort((a, b) => b.createdAt.compareTo(a.createdAt));
      if (!mounted) return;
      setState(() => _items = merged);
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
      child: _items.isEmpty
          ? ListView(
              padding: const EdgeInsets.all(MicrofiSpacing.page),
              children: [
                const SizedBox(height: 60),
                Center(
                  child: Column(
                    children: [
                      Icon(Icons.notifications_none_rounded, color: MicrofiColors.outline.withValues(alpha: 0.6), size: 40),
                      const SizedBox(height: 10),
                      Text(l10n.nhEmpty, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
                    ],
                  ),
                ),
              ],
            )
          : ListView.separated(
              padding: const EdgeInsets.all(MicrofiSpacing.page),
              itemCount: _items.length,
              separatorBuilder: (context, _) => const SizedBox(height: MicrofiSpacing.gapLg),
              itemBuilder: (context, i) => _NoticeCard(item: _items[i]),
            ),
    );
  }
}

class _NoticeCard extends StatelessWidget {
  final _NoticeItem item;

  const _NoticeCard({required this.item});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final isBroadcast = item.kind == _NoticeKind.broadcast;
    final local = item.createdAt.toLocal();
    final now = DateTime.now();
    final isToday = local.year == now.year && local.month == now.month && local.day == now.day;
    final dateLabel = isToday
        ? '${l10n.nhTodayLabel} · ${TimeOfDay.fromDateTime(local).format(context)}'
        : '${local.day}/${local.month}/${local.year} · ${TimeOfDay.fromDateTime(local).format(context)}';

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.lg),
        boxShadow: MicrofiShadows.softSmall,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(
              color: (isBroadcast ? MicrofiColors.primary : MicrofiColors.secondary).withValues(alpha: 0.1),
              shape: BoxShape.circle,
            ),
            child: Icon(
              isBroadcast ? Icons.campaign_outlined : Icons.info_outline_rounded,
              color: isBroadcast ? MicrofiColors.primary : MicrofiColors.secondary,
              size: 18,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: (isBroadcast ? MicrofiColors.primary : MicrofiColors.secondary).withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(MicrofiRadius.full),
                      ),
                      child: Text(
                        isBroadcast ? l10n.nhAnnouncementLabel : l10n.nhBranchNoticeLabel,
                        style: TextStyle(
                          fontSize: 10.5,
                          fontWeight: FontWeight.w700,
                          color: isBroadcast ? MicrofiColors.primary : MicrofiColors.secondary,
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(dateLabel, style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                  ],
                ),
                const SizedBox(height: 6),
                Text(item.message, style: const TextStyle(fontSize: 13, color: MicrofiColors.primary, height: 1.3)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
