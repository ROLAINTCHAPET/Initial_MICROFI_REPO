import 'dart:async';

import 'package:flutter/material.dart';
import '../../core/connectivity_service.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../../core/local_ceiling_cache.dart';
import '../../core/location.dart';
import '../../core/receipt_context_cache.dart';
import '../../core/status_components.dart';
import '../activation/sponsor_activation_screen.dart';
import '../collection/collection_repository.dart';
import '../collection/collection_stepper_screen.dart';
import '../collection/notification_repository.dart';
import '../collection/offline_queue_repository.dart';
import '../emergency/sos_ack_notification_cache.dart';
import '../emergency/sos_repository.dart';
import '../history/history_screen.dart';
import '../route/route_screen.dart';
import 'agent_profile.dart';
import 'branch_notice_repository.dart';
import 'branch_repository.dart';
import 'contact_branch.dart';
import 'home_repository.dart';
import 'reconciliation_confirm_screen.dart';
import 'reconciliation_repository.dart';
import '../../l10n/app_localizations.dart';

/// Graphical Design/agent/agent_dashboard — the Home tab body (no Scaffold/AppBar of its own;
/// AppShell supplies those). Agent header, escrow/ceiling gauge, primary CTA, quick actions and
/// a Recent Collections summary.
class HomeScreen extends StatefulWidget {
  final String token;

  const HomeScreen({super.key, required this.token});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late final HomeRepository _repository = HomeRepository(widget.token);
  late final CollectionRepository _collectionRepository = CollectionRepository(widget.token);

  AgentProfile? _profile;
  EscrowStatus? _escrow;
  List<CollectionSummary> _recent = [];
  String? _error;
  bool _loading = true;

  int _pendingCount = 0;
  bool _online = true;
  bool _syncing = false;
  StreamSubscription<bool>? _connectivitySub;

  bool _sendingSos = false;
  SosStatus? _pendingSos;
  Timer? _sosPollTimer;

  BranchNotice? _bannerNotice;
  String? _dismissedNoticeId;
  Timer? _noticePollTimer;

  int _pendingConfirmationCount = 0;
  Timer? _confirmationPollTimer;

  @override
  void initState() {
    super.initState();
    _load();
    ConnectivityService.instance.isOnline().then((online) {
      if (mounted) setState(() => _online = online);
    });
    _connectivitySub = ConnectivityService.instance.onOnlineChanged.listen((online) {
      if (mounted) setState(() => _online = online);
      // The moment the network comes back, push the queue up automatically — no reason to wait
      // for the agent to open Home and notice the pending badge, let alone tap a button.
      if (online) _syncNow();
    });
  }

  @override
  void dispose() {
    _connectivitySub?.cancel();
    _sosPollTimer?.cancel();
    _noticePollTimer?.cancel();
    _confirmationPollTimer?.cancel();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final profile = await _repository.fetchMyProfile();
      final results = await Future.wait([
        _repository.fetchEscrow(profile.id),
        _collectionRepository.listMine(),
      ]);
      final pending = await OfflineQueueRepository(profile.id).list();
      if (!mounted) return;
      final escrow = results[0] as EscrowStatus;
      setState(() {
        _profile = profile;
        _escrow = escrow;
        _recent = (results[1] as List<CollectionSummary>).take(5).toList();
        _pendingCount = pending.length;
      });
      LocalCeilingCache(profile.id).save(effectiveCeilingXaf: escrow.effectiveCeilingXaf, cumulativeTodayXaf: escrow.cumulativeTodayXaf);
      unawaited(_reportSyncStatusBestEffort(profile.id, pending.length));
      _refreshReceiptContext();
      _checkSosStatus();
      _checkBranchNotices();
      _noticePollTimer ??= Timer.periodic(const Duration(seconds: 60), (_) => _checkBranchNotices());
      _checkPendingConfirmations();
      _confirmationPollTimer ??= Timer.periodic(const Duration(seconds: 60), (_) => _checkPendingConfirmations());
      if (pending.isNotEmpty && await ConnectivityService.instance.isOnline()) _syncNow();
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(context, e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  // Best-effort refresh of the branch/org name an offline-composed receipt needs (see
  // OfflineReceiptComposer + ReceiptContextCache) — these change rarely, so a failure here just
  // means a slightly stale (or, on a fresh install before the first successful Home load, absent)
  // cache, never something worth surfacing as a Home-load error.
  Future<void> _refreshReceiptContext() async {
    try {
      final branchRepository = BranchRepository(widget.token);
      final results = await Future.wait([branchRepository.fetchMyBranch(), branchRepository.fetchMfiName()]);
      final branch = results[0] as AgentBranch;
      final mfiName = results[1] as String;
      await ReceiptContextCache().save(mfiName: mfiName, branchName: branch.name);
    } catch (_) {
      // Best-effort — retried on the next Home load.
    }
  }

  // Fully automatic (FR-07): each queued collection already carries the PIN the agent entered the
  // moment they made it (see PendingCollection.pin) — by sync time the cash is already collected
  // and the receipt already handed to the client, so re-confirming here would protect nothing that
  // PIN didn't already cover. Sync just uploads already-authorized data; no prompt, no tap needed.
  Future<void> _syncNow() async {
    if (_syncing) return;
    final profile = _profile;
    if (profile == null) return;
    final queueRepo = OfflineQueueRepository(profile.id);
    final pending = await queueRepo.list();
    if (pending.isEmpty) return;
    if (!mounted) return;

    setState(() => _syncing = true);
    try {
      final results = await _collectionRepository.syncBatch(
        pending.map((c) => c.toRequestBody()).toList(),
      );
      final succeeded = results.where((r) => r.success).map((r) => r.deviceTxId).toSet();
      await queueRepo.removeByDeviceTxIds(succeeded);
      // The client never got their SMS/notification for these at collection time (there was no
      // connectivity to send it) — fire it now that the server actually has the record, same call
      // an online collection makes right after it succeeds. Best-effort: a notify failure here
      // must never re-queue a collection that already synced successfully.
      final notificationRepository = NotificationRepository(widget.token);
      for (final result in results) {
        final collectionId = result.collectionId;
        if (result.success && collectionId != null) {
          unawaited(_notifyBestEffort(notificationRepository, collectionId));
        }
      }
      if (!mounted) return;
      await _load();
    } catch (_) {
      // Stays queued — will retry on the next connectivity change or app open.
    } finally {
      if (mounted) setState(() => _syncing = false);
    }
  }

  Future<void> _notifyBestEffort(NotificationRepository repository, String collectionId) async {
    try {
      await repository.notifyCollection(collectionId, printedReceipt: false);
    } catch (_) {
      // The collection already synced successfully — a notify failure here is not retried and
      // must never be treated as a sync failure.
    }
  }

  // Best-effort, same as _notifyBestEffort: this is purely informational for the server's OFJ
  // close-blocking gate (see HomeRepository#reportSyncStatus) — a failed report here must never
  // block the Home screen or get surfaced as an error, it just means the gate stays as accurate
  // as the last successful report until the next one goes through.
  Future<void> _reportSyncStatusBestEffort(String agentId, int pendingCount) async {
    try {
      await _repository.reportSyncStatus(agentId, pendingCount);
    } catch (_) {
      // Retried on the next _load() (app resume, connectivity change, or post-sync reload).
    }
  }

  Future<void> _collectCash() async {
    if (_profile == null) return;
    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => CollectionStepperScreen(token: widget.token, profile: _profile!),
      ),
    );
    if (result == true) _load();
  }

  void _openMyRoute() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => RouteScreen(token: widget.token)));
  }

  void _openHistory() {
    final l10n = AppLocalizations.of(context)!;
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(title: Text(l10n.hsCollectionHistoryTitle)),
        body: SafeArea(child: HistoryScreen(token: widget.token)),
      ),
    ));
  }

  void _openSponsorActivation() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => SponsorActivationScreen(token: widget.token)));
  }

  Future<void> _sendSos() async {
    if (_profile == null || _sendingSos) return;
    setState(() => _sendingSos = true);
    double? lat;
    double? lon;
    try {
      final position = await captureCurrentLocation();
      lat = position.latitude;
      lon = position.longitude;
    } catch (_) {
      // Best-effort: SOS must go through even without a GPS fix.
    }
    try {
      await SosRepository(widget.token, _profile!.id).raise(lat: lat, lon: lon);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.hsSosSentMessage), backgroundColor: MicrofiColors.error),
      );
      _startSosPolling();
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.hsSosSendFailedMessage)),
      );
    } finally {
      if (mounted) setState(() => _sendingSos = false);
    }
  }

  // UC-14: an agent who raised an SOS otherwise has no way to know their branch actually saw it
  // — this app has no push infrastructure, so a short foreground poll stands in for one. Checked
  // once on every screen load (catches an alert raised in a previous session, or acknowledged
  // while this screen wasn't mounted at all) and, while one is still unacknowledged, again every
  // 20s until it is — the same method handles both, via _startSosPolling calling this again.
  //
  // The "was this newly acknowledged" check is deliberately NOT based on comparing against
  // _pendingSos (in-memory state) — AppShell rebuilds HomeScreen from scratch on every tab
  // switch, which would silently destroy that in-memory "it was pending a moment ago" signal the
  // instant the agent glanced at another tab. SosAckNotificationCache persists that instead, so
  // the confirmation survives exactly that rebuild.
  Future<void> _checkSosStatus() async {
    final profile = _profile;
    if (profile == null) return;
    try {
      final events = await SosRepository(widget.token, profile.id).listMine();
      if (!mounted) return;

      final acknowledgedIds = events.where((e) => e.acknowledged).map((e) => e.id).toList();
      final newlyAcknowledged = await SosAckNotificationCache(profile.id).diffNewlyAcknowledged(acknowledgedIds);
      if (newlyAcknowledged.isNotEmpty && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.hsSosAcknowledgedMessage), backgroundColor: MicrofiColors.secondary),
        );
      }

      final unacknowledged = events.where((e) => !e.acknowledged).toList();
      if (unacknowledged.isNotEmpty) {
        setState(() => _pendingSos = unacknowledged.first);
        _startSosPolling();
      } else {
        _sosPollTimer?.cancel();
        if (_pendingSos != null) setState(() => _pendingSos = null);
      }
    } catch (_) {
      // Best-effort status check — silently retried on the next poll/screen load.
    }
  }

  // UC-15: no push infrastructure here either — same reasoning as SOS above, just for operational
  // notices like a same-day closing-time change. Polled less aggressively than SOS since these
  // aren't time-critical to the second; the SMS sent alongside this is what carries the urgency.
  Future<void> _checkBranchNotices() async {
    final profile = _profile;
    if (profile == null) return;
    try {
      final notices = await BranchNoticeRepository(widget.token).listMine();
      if (!mounted || notices.isEmpty) return;
      final latest = notices.first;
      if (latest.id == _dismissedNoticeId) return;
      setState(() => _bannerNotice = latest);
    } catch (_) {
      // Best-effort — silently retried on the next poll/screen load.
    }
  }

  // Same no-push-infrastructure reasoning as branch notices/SOS above — a cashier's physical
  // count still occupies this agent's escrow ceiling until they confirm it themselves (or it
  // auto-expires server-side), so surfacing this promptly actually matters to how much they can
  // collect next, not just informational.
  Future<void> _checkPendingConfirmations() async {
    if (_profile == null) return;
    try {
      final lines = await ReconciliationRepository(widget.token).listMyPendingConfirmations();
      if (!mounted) return;
      setState(() => _pendingConfirmationCount = lines.length);
    } catch (_) {
      // Best-effort — silently retried on the next poll/screen load.
    }
  }

  void _openPendingConfirmations() {
    Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => ReconciliationConfirmScreen(token: widget.token)))
        .then((_) => _checkPendingConfirmations());
  }

  void _dismissBranchNotice() {
    final notice = _bannerNotice;
    if (notice == null) return;
    setState(() {
      _dismissedNoticeId = notice.id;
      _bannerNotice = null;
    });
  }

  void _startSosPolling() {
    _sosPollTimer?.cancel();
    _sosPollTimer = Timer.periodic(const Duration(seconds: 20), (_) => _checkSosStatus());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loading && _profile == null) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null && _profile == null) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 80),
          const Icon(Icons.error_outline, color: MicrofiColors.error, size: 40),
          const SizedBox(height: 12),
          Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: MicrofiColors.error)),
          const SizedBox(height: 16),
          Center(child: FilledButton(onPressed: _load, child: Text(l10n.commonRetry))),
        ],
      );
    }

    final profile = _profile!;
    final escrow = _escrow;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(MicrofiSpacing.page),
        children: [
          Row(
            children: [
              CircleAvatar(
                radius: 22,
                backgroundColor: MicrofiColors.surfaceContainerHigh,
                child: Text(
                  profile.fullName.isNotEmpty ? profile.fullName[0].toUpperCase() : '?',
                  style: const TextStyle(color: MicrofiColors.primary, fontSize: 18, fontWeight: FontWeight.bold),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(profile.fullName, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.bold, color: MicrofiColors.primary)),
                    const SizedBox(height: 1),
                    Text(profile.employeeCode, style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              _SosButton(sending: _sendingSos, onTap: _sendSos),
            ],
          ),
          const SizedBox(height: 8),
          Align(alignment: Alignment.centerLeft, child: _StatusPill(status: profile.status)),
          const SizedBox(height: MicrofiSpacing.gapLg),
          if (_pendingSos != null) ...[
            _SosPendingBanner(),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_bannerNotice != null) ...[
            _BranchNoticeBanner(notice: _bannerNotice!, onDismiss: _dismissBranchNotice),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_pendingConfirmationCount > 0) ...[
            _PendingConfirmationBanner(count: _pendingConfirmationCount, onTap: _openPendingConfirmations),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_pendingCount > 0) ...[
            OfflineBanner(
              pendingCount: _pendingCount,
              syncing: _syncing,
              onSyncNow: _online ? _syncNow : null,
            ),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (escrow != null) _CeilingGaugeCard(escrow: escrow),
          const SizedBox(height: MicrofiSpacing.gapLg),
          SizedBox(
            width: double.infinity,
            height: 64,
            child: FilledButton(
              onPressed: _collectCash,
              style: FilledButton.styleFrom(
                backgroundColor: MicrofiColors.primary,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.add_circle, size: 20, color: Colors.white),
                  const SizedBox(width: 8),
                  Text(l10n.hsNewCollection, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                ],
              ),
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Row(
            children: [
              Expanded(child: _QuickAction(icon: Icons.map, label: l10n.hsMyRoute, onTap: _openMyRoute)),
              const SizedBox(width: MicrofiSpacing.gapLg),
              Expanded(child: _QuickAction(icon: Icons.history, label: l10n.hsQuickActionHistory, onTap: _openHistory)),
            ],
          ),
          const SizedBox(height: MicrofiSpacing.gap),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: _openSponsorActivation,
              icon: const Icon(Icons.how_to_reg, size: 16),
              label: Text(l10n.hsSponsorClientActivation, style: const TextStyle(fontSize: 13)),
              style: OutlinedButton.styleFrom(
                foregroundColor: MicrofiColors.primary,
                side: const BorderSide(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
                minimumSize: const Size.fromHeight(42),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
              ),
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gap),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: () => contactBranch(context, widget.token),
              icon: const Icon(Icons.call, size: 16),
              label: Text(l10n.commonContactBranch, style: const TextStyle(fontSize: 13)),
              style: FilledButton.styleFrom(
                backgroundColor: MicrofiColors.secondary,
                foregroundColor: Colors.white,
                minimumSize: const Size.fromHeight(42),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(l10n.hsRecentCollections, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
              TextButton(onPressed: _openHistory, child: Text(l10n.hsSeeAll, style: const TextStyle(fontSize: 13))),
            ],
          ),
          const Divider(color: MicrofiColors.outlineVariant, height: 1),
          if (_recent.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 20),
              child: Text(l10n.hsNoCollectionsRecorded, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
            )
          else
            ..._recent.map((c) => _RecentCollectionRow(collection: c)),
        ],
      ),
    );
  }
}

class _CeilingGaugeCard extends StatelessWidget {
  final EscrowStatus escrow;

  const _CeilingGaugeCard({required this.escrow});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.all(MicrofiSpacing.card + 2),
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(l10n.hsTodaysCollections, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.4)),
                  const SizedBox(height: 3),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.baseline,
                    textBaseline: TextBaseline.alphabetic,
                    children: [
                      Text(_fmt(escrow.cumulativeTodayXaf), style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                      const SizedBox(width: 5),
                      const Text('XAF', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: MicrofiColors.primaryContainer)),
                    ],
                  ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(l10n.hsCeilingLabel, style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                  Text(l10n.amountXaf(_fmt(escrow.effectiveCeilingXaf)), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
                ],
              ),
            ],
          ),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(MicrofiRadius.full),
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: escrow.utilization),
              duration: const Duration(milliseconds: 500),
              builder: (context, value, _) => LinearProgressIndicator(
                value: value,
                minHeight: 10,
                backgroundColor: MicrofiColors.surfaceContainerHigh,
                valueColor: AlwaysStoppedAnimation(escrow.nearLimit ? MicrofiColors.error : MicrofiColors.primary),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Center(
            child: Text(
              escrow.nearLimit
                  ? l10n.hsCapacityReachedDepositSoon((escrow.utilization * 100).round())
                  : l10n.hsCapacityReachedSafe((escrow.utilization * 100).round()),
              style: TextStyle(fontSize: 11, color: escrow.nearLimit ? MicrofiColors.error : MicrofiColors.outline),
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

class _QuickAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _QuickAction({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: MicrofiColors.surfaceContainerLowest,
      borderRadius: BorderRadius.circular(MicrofiRadius.md),
      child: InkWell(
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        onTap: onTap,
        child: Container(
          height: 64,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(MicrofiRadius.md),
            border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, color: MicrofiColors.primary, size: 20),
              const SizedBox(height: 5),
              Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 11, color: MicrofiColors.primary)),
            ],
          ),
        ),
      ),
    );
  }
}

class _RecentCollectionRow extends StatelessWidget {
  final CollectionSummary collection;

  const _RecentCollectionRow({required this.collection});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final time = TimeOfDay.fromDateTime(collection.collectedAt.toLocal()).format(context);
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
                Text(collection.clientName ?? l10n.hsUnknownClient, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
                Text(l10n.hsTimeCashLine(time), style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
              ],
            ),
          ),
          Text(l10n.hsAmountCollectedPlus(_fmt(collection.amountXaf)), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.secondary)),
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

class _StatusPill extends StatelessWidget {
  final String status;

  const _StatusPill({required this.status});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final active = status == 'ACTIVE';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: (active ? MicrofiColors.secondary : MicrofiColors.error).withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(MicrofiRadius.full),
        border: Border.all(color: active ? MicrofiColors.secondary : MicrofiColors.error),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 6, height: 6, decoration: BoxDecoration(color: active ? MicrofiColors.secondary : MicrofiColors.error, shape: BoxShape.circle)),
          const SizedBox(width: 5),
          Text(active ? l10n.hsStatusActive : l10n.hsStatusSuspended, style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: active ? MicrofiColors.secondary : MicrofiColors.error)),
        ],
      ),
    );
  }
}

/// UC-15 — a same-day schedule change surfaced here since there's no push channel to rely on;
/// dismissible since, unlike the SOS banner, there's nothing further for the agent to do about it.
class _BranchNoticeBanner extends StatelessWidget {
  final BranchNotice notice;
  final VoidCallback onDismiss;

  const _BranchNoticeBanner({required this.notice, required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: MicrofiColors.secondaryContainer,
        borderRadius: BorderRadius.circular(MicrofiRadius.sm),
      ),
      child: Row(
        children: [
          const Icon(Icons.info_outline, color: MicrofiColors.onSecondaryContainer, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              notice.message,
              style: const TextStyle(fontSize: 12.5, color: MicrofiColors.onSecondaryContainer, fontWeight: FontWeight.w600),
            ),
          ),
          InkWell(
            onTap: onDismiss,
            borderRadius: BorderRadius.circular(MicrofiRadius.full),
            child: const Padding(
              padding: EdgeInsets.all(2),
              child: Icon(Icons.close, color: MicrofiColors.onSecondaryContainer, size: 16),
            ),
          ),
        ],
      ),
    );
  }
}

/// Tappable, not dismissible like the branch-notice banner above — there's an actual action to
/// take here (confirm or request rejection), and the cash counted stays tied up until it's taken.
class _PendingConfirmationBanner extends StatelessWidget {
  final int count;
  final VoidCallback onTap;

  const _PendingConfirmationBanner({required this.count, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(MicrofiRadius.sm),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: MicrofiColors.tertiaryFixed,
          borderRadius: BorderRadius.circular(MicrofiRadius.sm),
        ),
        child: Row(
          children: [
            const Icon(Icons.fact_check_outlined, color: MicrofiColors.onTertiaryFixedVariant, size: 18),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                l10n.hsPendingConfirmationBanner(count),
                style: const TextStyle(fontSize: 12.5, color: MicrofiColors.onTertiaryFixedVariant, fontWeight: FontWeight.w600),
              ),
            ),
            const Icon(Icons.chevron_right, color: MicrofiColors.onTertiaryFixedVariant, size: 18),
          ],
        ),
      ),
    );
  }
}

/// One tap, no confirmation — a real emergency shouldn't wait on a dialog.
class _SosPendingBanner extends StatelessWidget {
  const _SosPendingBanner();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: MicrofiColors.errorContainer,
        borderRadius: BorderRadius.circular(MicrofiRadius.sm),
      ),
      child: Row(
        children: [
          const Icon(Icons.emergency, color: MicrofiColors.onErrorContainer, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              AppLocalizations.of(context)!.hsSosPendingBanner,
              style: const TextStyle(fontSize: 12.5, color: MicrofiColors.onErrorContainer, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    );
  }
}

class _SosButton extends StatelessWidget {
  final bool sending;
  final VoidCallback onTap;

  const _SosButton({required this.sending, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: MicrofiColors.error,
      shape: const CircleBorder(),
      child: InkWell(
        customBorder: const CircleBorder(),
        onTap: sending ? null : onTap,
        child: SizedBox(
          width: 42,
          height: 42,
          child: Center(
            child: sending
                ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Icon(Icons.emergency, color: Colors.white, size: 20),
          ),
        ),
      ),
    );
  }
}
