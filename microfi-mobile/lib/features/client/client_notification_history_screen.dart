import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_models.dart';
import 'client_repository.dart';
import '../../l10n/app_localizations.dart';

/// Client-facing history of Back-Office announcements (UC-15) — the Home screen only ever pops
/// up today's latest one (see ClientHomeScreen._isToday), so this is the only place a client can
/// re-read an older one. Same RECENT_LIMIT=20 cap as the server's "recent" endpoint.
class ClientNotificationHistoryScreen extends StatefulWidget {
  final String token;

  const ClientNotificationHistoryScreen({super.key, required this.token});

  @override
  State<ClientNotificationHistoryScreen> createState() => _ClientNotificationHistoryScreenState();
}

class _ClientNotificationHistoryScreenState extends State<ClientNotificationHistoryScreen> {
  late final ClientSelfRepository _repository = ClientSelfRepository(widget.token);

  List<ClientBroadcastMessage> _items = [];
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
      final results = await _repository.fetchBroadcasts();
      results.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      if (!mounted) return;
      setState(() => _items = results);
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
              itemBuilder: (context, i) => _ClientNoticeCard(item: _items[i]),
            ),
    );
  }
}

class _ClientNoticeCard extends StatelessWidget {
  final ClientBroadcastMessage item;

  const _ClientNoticeCard({required this.item});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
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
            decoration: BoxDecoration(color: MicrofiColors.primary.withValues(alpha: 0.1), shape: BoxShape.circle),
            child: const Icon(Icons.campaign_outlined, color: MicrofiColors.primary, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(dateLabel, style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
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
