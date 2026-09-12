import 'dart:async';

import 'package:flutter/material.dart';
import '../../core/animated_entrance.dart';
import '../../core/connectivity_service.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../../core/local_ceiling_cache.dart';
import '../../core/local_geofence_cache.dart';
import '../../core/local_schedule_cache.dart';
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
import 'broadcast_repository.dart';
import 'branch_repository.dart';
import 'collection_rejection_notification_cache.dart';
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

  BroadcastMessage? _bannerBroadcast;
  String? _dismissedBroadcastId;
  Timer? _broadcastPollTimer;

  int _pendingConfirmationCount = 0;
  int _pendingConfirmationTotalXaf = 0;
  Timer? _confirmationPollTimer;

  Timer? _rejectionDecisionPollTimer;

  int _exportableCount = 0;
  int _exportableTotalXaf = 0;
  Timer? _exportablePollTimer;
  bool _endingDay = false;

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
    _broadcastPollTimer?.cancel();
    _confirmationPollTimer?.cancel();
    _rejectionDecisionPollTimer?.cancel();
    _exportablePollTimer?.cancel();
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
      unawaited(_refreshGeofenceCache(profile.id));
      unawaited(_refreshScheduleCache(profile.id));
      unawaited(_reportSyncStatusBestEffort(profile.id, pending.length));
      _refreshReceiptContext();
      _checkSosStatus();
      _checkBranchNotices();
      _noticePollTimer ??= Timer.periodic(const Duration(seconds: 60), (_) => _checkBranchNotices());
      _checkBroadcasts();
      _broadcastPollTimer ??= Timer.periodic(const Duration(seconds: 60), (_) => _checkBroadcasts());
      _checkPendingConfirmations();
      _confirmationPollTimer ??= Timer.periodic(const Duration(seconds: 60), (_) => _checkPendingConfirmations());
      _checkRejectionDecisions();
      _rejectionDecisionPollTimer ??= Timer.periodic(const Duration(seconds: 60), (_) => _checkRejectionDecisions());
      _checkExportable();
      _exportablePollTimer ??= Timer.periodic(const Duration(seconds: 60), (_) => _checkExportable());
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

  /// Keeps LocalGeofenceCache fresh so an offline collection has something recent to check
  /// against — a failed fetch here just leaves the previous snapshot in place, same
  /// best-effort reasoning as [_reportSyncStatusBestEffort].
  Future<void> _refreshGeofenceCache(String agentId) async {
    try {
      final geofence = await _repository.fetchGeofence();
      await LocalGeofenceCache(agentId).save(geofence);
    } catch (_) {
      // Retried on the next _load().
    }
  }

  /// Keeps LocalScheduleCache fresh so an offline collection has something recent to check
  /// against — same best-effort reasoning as [_refreshGeofenceCache].
  Future<void> _refreshScheduleCache(String agentId) async {
    try {
      final branch = await BranchRepository(widget.token).fetchMyBranch();
      await LocalScheduleCache(agentId).save(openTime: branch.openTime, closeTime: branch.closeTime);
    } catch (_) {
      // Retried on the next _load().
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
      // A notice only pops up on the Home screen the day it was sent — older ones are still
      // reachable from the notification history screen, but re-surfacing them here on a later
      // day would make a stale operational notice look like today's news.
      if (!_isToday(latest.createdAt) || latest.id == _dismissedNoticeId) return;
      setState(() => _bannerNotice = latest);
    } catch (_) {
      // Best-effort — silently retried on the next poll/screen load.
    }
  }

  // ADMIN/BRANCH_MANAGER announcement to every agent — same no-push-infrastructure reasoning as
  // branch notices, just a distinct message type (and distinct dismiss-tracking) so dismissing
  // one never hides the other.
  Future<void> _checkBroadcasts() async {
    final profile = _profile;
    if (profile == null) return;
    try {
      final broadcasts = await BroadcastRepository(widget.token).listMine();
      if (!mounted || broadcasts.isEmpty) return;
      final latest = broadcasts.first;
      if (!_isToday(latest.createdAt) || latest.id == _dismissedBroadcastId) return;
      setState(() => _bannerBroadcast = latest);
    } catch (_) {
      // Best-effort — silently retried on the next poll/screen load.
    }
  }

  bool _isToday(DateTime utc) {
    final local = utc.toLocal();
    final now = DateTime.now();
    return local.year == now.year && local.month == now.month && local.day == now.day;
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
      setState(() {
        _pendingConfirmationCount = lines.length;
        _pendingConfirmationTotalXaf = lines.fold(0, (sum, line) => sum + line.totalXaf);
      });
    } catch (_) {
      // Best-effort — silently retried on the next poll/screen load.
    }
  }

  // Same no-push-infrastructure reasoning as SOS/branch-notices/pending-confirmations above — an
  // agent who asked to void a collection for error has no way to learn a manager/admin decided it
  // except by polling. Scoped to APPROVED/DENIED only (never PENDING): the notification is about
  // the *decision*, and CollectionRejectionNotificationCache's persisted diff (same reasoning as
  // SosAckNotificationCache) is what stops the same decision from being announced again after
  // AppShell rebuilds this screen from scratch on a tab switch.
  Future<void> _checkRejectionDecisions() async {
    final profile = _profile;
    if (profile == null) return;
    try {
      final requests = await ReconciliationRepository(widget.token).listMyRejectionRequests();
      if (!mounted) return;

      final decided = requests.where((r) => r.status == 'APPROVED' || r.status == 'DENIED').toList();
      final newlyDecided = await CollectionRejectionNotificationCache(profile.id)
          .diffNewlyDecided(decided.map((r) => r.id).toList());
      if (newlyDecided.isEmpty || !mounted) return;

      final l10n = AppLocalizations.of(context)!;
      for (final request in decided.where((r) => newlyDecided.contains(r.id))) {
        if (!mounted) return;
        final message = request.status == 'APPROVED'
            ? l10n.hsRejectionApprovedMessage
            : l10n.hsRejectionDeniedMessage(request.decisionReason ?? '');
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(message), backgroundColor: MicrofiColors.secondary),
        );
      }
    } catch (_) {
      // Best-effort — silently retried on the next poll/screen load.
    }
  }

  // Backs the "End My Day" banner: confirmed cash sits unexported until this agent pushes it
  // themselves, the branch's session closes, or the scheduled closing-time job catches it — this
  // just tells the agent there's something ready right now, same polling reasoning as everything
  // else on this screen.
  Future<void> _checkExportable() async {
    if (_profile == null) return;
    try {
      final summary = await ReconciliationRepository(widget.token).fetchExportableSummary();
      if (!mounted) return;
      setState(() {
        _exportableCount = summary.readyCount;
        _exportableTotalXaf = summary.readyTotalXaf;
      });
    } catch (_) {
      // Best-effort — silently retried on the next poll/screen load.
    }
  }

  Future<void> _endMyDay() async {
    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text(l10n.hsEndDayConfirmDialogTitle),
        content: Text(l10n.hsEndDayConfirmDialogMessage(_exportableCount, _fmt(_exportableTotalXaf))),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(l10n.commonCancel)),
          FilledButton(onPressed: () => Navigator.of(context).pop(true), child: Text(l10n.hsEndDayButton)),
        ],
      ),
    );
    if (confirmed != true) return;

    setState(() => _endingDay = true);
    try {
      final result = await ReconciliationRepository(widget.token).endMyDay();
      if (!mounted) return;
      await showSuccessDialog(context, l10n.hsEndDaySuccess(result.exportedCount, _fmt(result.exportedTotalXaf)));
      _checkExportable();
    } catch (e) {
      if (!mounted) return;
      await showErrorDialog(context, e, title: l10n.hsEndDayFailed);
    } finally {
      if (mounted) setState(() => _endingDay = false);
    }
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

  void _dismissBroadcast() {
    final broadcast = _bannerBroadcast;
    if (broadcast == null) return;
    setState(() {
      _dismissedBroadcastId = broadcast.id;
      _bannerBroadcast = null;
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
        padding: const EdgeInsets.fromLTRB(MicrofiSpacing.page, MicrofiSpacing.page, MicrofiSpacing.page, MicrofiSpacing.page + 8),
        children: [
          _HomeHeader(profile: profile, sendingSos: _sendingSos, onSos: _sendSos),
          const SizedBox(height: MicrofiSpacing.gapLg),
          if (_pendingSos != null) ...[
            FadeSlideIn(child: _SosPendingBanner()),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_bannerNotice != null) ...[
            FadeSlideIn(child: _BranchNoticeBanner(notice: _bannerNotice!, onDismiss: _dismissBranchNotice)),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_bannerBroadcast != null) ...[
            FadeSlideIn(child: _BroadcastBanner(broadcast: _bannerBroadcast!, onDismiss: _dismissBroadcast)),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_pendingConfirmationCount > 0) ...[
            FadeSlideIn(child: _PendingConfirmationBanner(count: _pendingConfirmationCount, onTap: _openPendingConfirmations)),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_exportableCount > 0) ...[
            FadeSlideIn(child: _EndDayBanner(count: _exportableCount, totalXaf: _exportableTotalXaf, sending: _endingDay, onTap: _endingDay ? null : _endMyDay)),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (_pendingCount > 0) ...[
            FadeSlideIn(
              child: OfflineBanner(
                pendingCount: _pendingCount,
                syncing: _syncing,
                onSyncNow: _online ? _syncNow : null,
              ),
            ),
            const SizedBox(height: MicrofiSpacing.gapLg),
          ],
          if (escrow != null)
            FadeSlideIn(child: _CeilingGaugeCard(escrow: escrow, pendingConfirmationTotalXaf: _pendingConfirmationTotalXaf)),
          const SizedBox(height: MicrofiSpacing.gapLg),
          _PrimaryCtaButton(icon: Icons.add_circle_rounded, label: l10n.hsNewCollection, onTap: _collectCash),
          const SizedBox(height: 20),
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Expanded(child: Divider(color: MicrofiColors.outlineVariant.withValues(alpha: 0.6))),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10),
                child: Text(
                  l10n.hsQuickActionsSectionLabel,
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.6),
                ),
              ),
              Expanded(child: Divider(color: MicrofiColors.outlineVariant.withValues(alpha: 0.6))),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: _QuickAction(
                  icon: Icons.map_rounded,
                  label: l10n.hsMyRoute,
                  accent: MicrofiColors.primary,
                  onTap: _openMyRoute,
                ),
              ),
              const SizedBox(width: MicrofiSpacing.gapLg),
              Expanded(
                child: _QuickAction(
                  icon: Icons.history_rounded,
                  label: l10n.hsQuickActionHistory,
                  accent: MicrofiColors.secondary,
                  onTap: _openHistory,
                ),
              ),
            ],
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Row(
            children: [
              Expanded(
                child: _QuickAction(
                  icon: Icons.how_to_reg_rounded,
                  label: l10n.hsSponsorClientActivation,
                  accent: MicrofiColors.onTertiaryFixedVariant,
                  onTap: _openSponsorActivation,
                ),
              ),
              const SizedBox(width: MicrofiSpacing.gapLg),
              Expanded(
                child: _QuickAction(
                  icon: Icons.call_rounded,
                  label: l10n.commonContactBranch,
                  accent: MicrofiColors.secondary,
                  onTap: () => contactBranch(context, widget.token),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(l10n.hsRecentCollections, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
              TextButton(onPressed: _openHistory, child: Text(l10n.hsSeeAll, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600))),
            ],
          ),
          const SizedBox(height: 4),
          Container(
            decoration: BoxDecoration(
              color: MicrofiColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(MicrofiRadius.lg),
              boxShadow: MicrofiShadows.soft,
            ),
            padding: const EdgeInsets.symmetric(horizontal: 6),
            child: _recent.isEmpty
                ? Padding(
                    padding: const EdgeInsets.symmetric(vertical: 28),
                    child: Center(
                      child: Column(
                        children: [
                          Icon(Icons.savings_outlined, size: 30, color: MicrofiColors.outline.withValues(alpha: 0.6)),
                          const SizedBox(height: 8),
                          Text(l10n.hsNoCollectionsRecorded, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
                        ],
                      ),
                    ),
                  )
                : Column(
                    children: [
                      for (int i = 0; i < _recent.length; i++) ...[
                        _RecentCollectionRow(collection: _recent[i]),
                        if (i < _recent.length - 1)
                          Divider(height: 1, indent: 58, color: MicrofiColors.outlineVariant.withValues(alpha: 0.5)),
                      ],
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

/// The friendlier "hero" replacement for the old plain avatar+name row — a soft navy-to-container
/// gradient card carrying the greeting, employee code, live status pill and the SOS button, so the
/// very top of Home reads as a welcome rather than a data row.
class _HomeHeader extends StatelessWidget {
  final AgentProfile profile;
  final bool sendingSos;
  final VoidCallback onSos;

  const _HomeHeader({required this.profile, required this.sendingSos, required this.onSos});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final active = profile.status == 'ACTIVE';
    return Container(
      padding: const EdgeInsets.fromLTRB(18, 18, 18, 16),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(MicrofiRadius.lg),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [MicrofiColors.primary, MicrofiColors.primaryContainer],
        ),
        boxShadow: MicrofiShadows.soft,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 50,
                height: 50,
                alignment: Alignment.center,
                decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle),
                child: Text(
                  profile.fullName.isNotEmpty ? profile.fullName[0].toUpperCase() : '?',
                  style: const TextStyle(color: MicrofiColors.primary, fontSize: 20, fontWeight: FontWeight.w800),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.hsGreeting,
                      style: TextStyle(fontSize: 12, color: Colors.white.withValues(alpha: 0.75), fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 1),
                    Text(
                      profile.fullName,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800, color: Colors.white),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 10),
              _SosButton(sending: sendingSos, onTap: onSos),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(MicrofiRadius.full),
                ),
                child: Text(profile.employeeCode, style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: Colors.white)),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(MicrofiRadius.full),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 6,
                      height: 6,
                      decoration: BoxDecoration(color: active ? MicrofiColors.secondaryFixed : MicrofiColors.errorContainer, shape: BoxShape.circle),
                    ),
                    const SizedBox(width: 5),
                    Text(
                      active ? l10n.hsStatusActive : l10n.hsStatusSuspended,
                      style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, color: Colors.white),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

/// The main "count cash → escrow ceiling" summary — elevated with a soft shadow (rather than a
/// hard outline) and a rounder gradient-filled gauge so it reads as this screen's centerpiece.
class _CeilingGaugeCard extends StatelessWidget {
  final EscrowStatus escrow;
  // escrow.cumulativeTodayXaf combines two different things that occupy the escrow ceiling for
  // different reasons — cash the cashier hasn't physically counted yet, and cash already counted
  // but still awaiting THIS agent's own confirmation (see CollectionReconciliationStatus's doc).
  // Showing only the combined total reads as "cumulates the reconciled and the non-reconciled"
  // (a real point of confusion raised live) — this breaks out the confirmation-pending portion
  // specifically so an agent can tell "not yet counted" apart from "counted, waiting on me".
  final int pendingConfirmationTotalXaf;

  const _CeilingGaugeCard({required this.escrow, required this.pendingConfirmationTotalXaf});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.lg),
        boxShadow: MicrofiShadows.soft,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 38,
                    height: 38,
                    margin: const EdgeInsets.only(top: 2, right: 10),
                    decoration: BoxDecoration(
                      color: MicrofiColors.primary.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(MicrofiRadius.sm),
                    ),
                    child: const Icon(Icons.account_balance_wallet_rounded, color: MicrofiColors.primary, size: 19),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l10n.hsTodaysCollections, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.4)),
                      const SizedBox(height: 3),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.baseline,
                        textBaseline: TextBaseline.alphabetic,
                        children: [
                          Text(_fmt(escrow.cumulativeTodayXaf), style: const TextStyle(fontSize: 25, fontWeight: FontWeight.w800, color: MicrofiColors.primary)),
                          const SizedBox(width: 5),
                          const Text('XAF', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: MicrofiColors.primaryContainer)),
                        ],
                      ),
                      if (pendingConfirmationTotalXaf > 0) ...[
                        const SizedBox(height: 2),
                        Text(
                          AppLocalizations.of(context)!.hsOfWhichAwaitingConfirmation(_fmt(pendingConfirmationTotalXaf)),
                          style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant),
                        ),
                      ],
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
          const SizedBox(height: 14),
          ClipRRect(
            borderRadius: BorderRadius.circular(MicrofiRadius.full),
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: escrow.utilization),
              duration: const Duration(milliseconds: 500),
              builder: (context, value, _) => LinearProgressIndicator(
                value: value,
                minHeight: 12,
                backgroundColor: MicrofiColors.surfaceContainerHigh,
                valueColor: AlwaysStoppedAnimation(escrow.nearLimit ? MicrofiColors.error : MicrofiColors.secondary),
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

/// The single, unmissable "start a collection" action — deliberately its own widget (rather than
/// inlined FilledButton styling) so its shadow/gradient treatment reads as clearly the highest
/// priority thing on the screen, above the quick-action grid below it.
class _PrimaryCtaButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _PrimaryCtaButton({required this.icon, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(MicrofiRadius.lg),
        boxShadow: [BoxShadow(color: MicrofiColors.secondary.withValues(alpha: 0.35), blurRadius: 18, offset: const Offset(0, 8))],
      ),
      child: Material(
        color: MicrofiColors.secondary,
        borderRadius: BorderRadius.circular(MicrofiRadius.lg),
        child: InkWell(
          borderRadius: BorderRadius.circular(MicrofiRadius.lg),
          onTap: onTap,
          child: Container(
            height: 62,
            alignment: Alignment.center,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(icon, size: 24, color: Colors.white),
                const SizedBox(width: 10),
                Text(label, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: Colors.white)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _QuickAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color accent;
  final VoidCallback onTap;

  const _QuickAction({required this.icon, required this.label, required this.accent, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        boxShadow: MicrofiShadows.softSmall,
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        child: InkWell(
          borderRadius: BorderRadius.circular(MicrofiRadius.md),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 14),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(color: accent.withValues(alpha: 0.1), shape: BoxShape.circle),
                  child: Icon(icon, color: accent, size: 19),
                ),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  child: Text(
                    label,
                    textAlign: TextAlign.center,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 11.5, color: MicrofiColors.primary),
                  ),
                ),
              ],
            ),
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
                Text(collection.clientName ?? l10n.hsUnknownClient, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                Text(l10n.hsTimeCashLine(time), style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
              ],
            ),
          ),
          Text(l10n.hsAmountCollectedPlus(_fmt(collection.amountXaf)), style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w700, color: MicrofiColors.secondary)),
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

/// UC-15 — a same-day schedule change surfaced here since there's no push channel to rely on;
/// dismissible since, unlike the SOS banner, there's nothing further for the agent to do about it.
class _BranchNoticeBanner extends StatelessWidget {
  final BranchNotice notice;
  final VoidCallback onDismiss;

  const _BranchNoticeBanner({required this.notice, required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    return _AlertBanner(
      icon: Icons.info_outline_rounded,
      background: MicrofiColors.secondaryContainer,
      foreground: MicrofiColors.onSecondaryContainer,
      message: notice.message,
      trailing: InkWell(
        onTap: onDismiss,
        borderRadius: BorderRadius.circular(MicrofiRadius.full),
        child: const Padding(
          padding: EdgeInsets.all(2),
          child: Icon(Icons.close_rounded, color: MicrofiColors.onSecondaryContainer, size: 18),
        ),
      ),
    );
  }
}

/// An ADMIN/BRANCH_MANAGER announcement — distinct message type from a branch notice (network-wide
/// or branch-scoped, sender-authored rather than an automatic schedule-change record), so it gets
/// its own icon/color even though the banner shape and dismiss behavior are identical.
class _BroadcastBanner extends StatelessWidget {
  final BroadcastMessage broadcast;
  final VoidCallback onDismiss;

  const _BroadcastBanner({required this.broadcast, required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    return _AlertBanner(
      icon: Icons.campaign_outlined,
      background: MicrofiColors.primaryContainer,
      foreground: MicrofiColors.onPrimary,
      message: broadcast.message,
      trailing: InkWell(
        onTap: onDismiss,
        borderRadius: BorderRadius.circular(MicrofiRadius.full),
        child: const Padding(
          padding: EdgeInsets.all(2),
          child: Icon(Icons.close_rounded, color: MicrofiColors.onPrimary, size: 18),
        ),
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
    return _AlertBanner(
      icon: Icons.fact_check_outlined,
      background: MicrofiColors.tertiaryFixed,
      foreground: MicrofiColors.onTertiaryFixedVariant,
      message: l10n.hsPendingConfirmationBanner(count),
      onTap: onTap,
      trailing: const Icon(Icons.chevron_right_rounded, color: MicrofiColors.onTertiaryFixedVariant, size: 20),
    );
  }
}

/// Confirmed cash sitting unexported until this agent pushes it themselves — the tap triggers the
/// push directly (behind a confirm dialog, see HomeScreen._endMyDay), it doesn't navigate anywhere.
class _EndDayBanner extends StatelessWidget {
  final int count;
  final int totalXaf;
  final bool sending;
  final VoidCallback? onTap;

  const _EndDayBanner({required this.count, required this.totalXaf, required this.sending, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return _AlertBanner(
      icon: Icons.cloud_upload_outlined,
      background: MicrofiColors.secondaryFixed,
      foreground: MicrofiColors.onSecondaryFixedVariant,
      message: l10n.hsEndDayBanner(count, _fmt(totalXaf)),
      onTap: onTap,
      trailing: sending
          ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: MicrofiColors.onSecondaryFixedVariant))
          : const Icon(Icons.chevron_right_rounded, color: MicrofiColors.onSecondaryFixedVariant, size: 20),
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

/// One tap, no confirmation — a real emergency shouldn't wait on a dialog.
class _SosPendingBanner extends StatelessWidget {
  const _SosPendingBanner();

  @override
  Widget build(BuildContext context) {
    return _AlertBanner(
      icon: Icons.emergency_rounded,
      background: MicrofiColors.errorContainer,
      foreground: MicrofiColors.onErrorContainer,
      message: AppLocalizations.of(context)!.hsSosPendingBanner,
    );
  }
}

/// Shared visual family for every Home banner — colored icon disc + message (+ optional trailing
/// action/spinner), softly elevated and rounded so the whole stack of alerts reads as one
/// consistent language instead of five differently-styled flat containers.
class _AlertBanner extends StatelessWidget {
  final IconData icon;
  final Color background;
  final Color foreground;
  final String message;
  final Widget? trailing;
  final VoidCallback? onTap;

  const _AlertBanner({
    required this.icon,
    required this.background,
    required this.foreground,
    required this.message,
    this.trailing,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final content = Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        boxShadow: MicrofiShadows.softSmall,
      ),
      child: Row(
        children: [
          Container(
            width: 34,
            height: 34,
            decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.35), shape: BoxShape.circle),
            child: Icon(icon, color: foreground, size: 18),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(message, style: TextStyle(fontSize: 12.5, color: foreground, fontWeight: FontWeight.w600)),
          ),
          if (trailing != null) ...[const SizedBox(width: 6), trailing!],
        ],
      ),
    );
    if (onTap == null) return content;
    return InkWell(onTap: onTap, borderRadius: BorderRadius.circular(MicrofiRadius.md), child: content);
  }
}

class _SosButton extends StatelessWidget {
  final bool sending;
  final VoidCallback onTap;

  const _SosButton({required this.sending, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 10, offset: const Offset(0, 3))],
        border: Border.all(color: Colors.white.withValues(alpha: 0.5), width: 2),
      ),
      child: Material(
        color: MicrofiColors.error,
        shape: const CircleBorder(),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: sending ? null : onTap,
          child: SizedBox(
            width: 44,
            height: 44,
            child: Center(
              child: sending
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.emergency_rounded, color: Colors.white, size: 21),
            ),
          ),
        ),
      ),
    );
  }
}
