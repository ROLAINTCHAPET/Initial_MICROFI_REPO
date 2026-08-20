import 'dart:async';

import 'package:flutter/material.dart';
import '../../core/connectivity_service.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import '../../core/location.dart';
import '../../core/status_components.dart';
import '../activation/sponsor_activation_screen.dart';
import '../collection/collection_repository.dart';
import '../collection/collection_stepper_screen.dart';
import '../collection/offline_queue_repository.dart';
import '../emergency/sos_repository.dart';
import '../history/history_screen.dart';
import '../route/route_screen.dart';
import 'agent_profile.dart';
import 'contact_branch.dart';
import 'home_repository.dart';

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

  @override
  void initState() {
    super.initState();
    _load();
    ConnectivityService.instance.isOnline().then((online) {
      if (mounted) setState(() => _online = online);
    });
    _connectivitySub = ConnectivityService.instance.onOnlineChanged.listen((online) {
      if (mounted) setState(() => _online = online);
    });
  }

  @override
  void dispose() {
    _connectivitySub?.cancel();
    _sosPollTimer?.cancel();
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
      setState(() {
        _profile = profile;
        _escrow = results[0] as EscrowStatus;
        _recent = (results[1] as List<CollectionSummary>).take(5).toList();
        _pendingCount = pending.length;
      });
      _checkSosStatus();
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _syncNow() async {
    final profile = _profile;
    if (profile == null) return;
    final queueRepo = OfflineQueueRepository(profile.id);
    final pending = await queueRepo.list();
    if (pending.isEmpty) return;
    if (!mounted) return;

    // The PIN is never stored with the queued items (see PendingCollection) — prompted once here
    // and applied to every item in this batch instead.
    final pin = await promptForPin(
      context,
      message: 'Confirm ${pending.length} pending collection${pending.length == 1 ? '' : 's'} totaling '
          '${pending.fold(0, (sum, c) => sum + c.amountXaf)} XAF.',
    );
    if (pin == null || pin.isEmpty) return;

    setState(() => _syncing = true);
    try {
      final results = await _collectionRepository.syncBatch(
        pending.map((c) => {...c.toRequestBody(), 'pin': pin}).toList(),
      );
      final succeeded = results.where((r) => r.success).map((r) => r.deviceTxId).toSet();
      await queueRepo.removeByDeviceTxIds(succeeded);
      if (!mounted) return;
      await _load();
    } catch (_) {
      // Stays queued — will retry on next Sync Now or next successful submit's connectivity check.
    } finally {
      if (mounted) setState(() => _syncing = false);
    }
  }

  Future<void> _collectCash() async {
    if (_profile == null) return;
    final result = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => CollectionStepperScreen(token: widget.token, agentId: _profile!.id),
      ),
    );
    if (result == true) _load();
  }

  void _openMyRoute() {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => RouteScreen(token: widget.token)));
  }

  void _openHistory() {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(title: const Text('Collection History')),
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
        const SnackBar(content: Text('SOS sent — your branch has been alerted.'), backgroundColor: MicrofiColors.error),
      );
      _startSosPolling();
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Unable to send SOS — check your connection and try again.')),
      );
    } finally {
      if (mounted) setState(() => _sendingSos = false);
    }
  }

  // UC-14: an agent who raised an SOS otherwise has no way to know their branch actually saw it
  // — this app has no push notifications, so a short foreground poll is what stands in for one.
  // Checked once on every screen load (catches an alert raised in a previous session) and, while
  // one is still unacknowledged, again every 20s until it is.
  Future<void> _checkSosStatus() async {
    final profile = _profile;
    if (profile == null) return;
    try {
      final events = await SosRepository(widget.token, profile.id).listMine();
      final unacknowledged = events.where((e) => !e.acknowledged).toList();
      if (!mounted) return;
      if (unacknowledged.isNotEmpty) {
        setState(() => _pendingSos = unacknowledged.first);
        _startSosPolling();
      } else if (_pendingSos != null) {
        setState(() => _pendingSos = null);
      }
    } catch (_) {
      // Best-effort status check — silently retried on the next poll/screen load.
    }
  }

  void _startSosPolling() {
    _sosPollTimer?.cancel();
    _sosPollTimer = Timer.periodic(const Duration(seconds: 20), (_) => _pollSosAcknowledgement());
  }

  Future<void> _pollSosAcknowledgement() async {
    final profile = _profile;
    final pending = _pendingSos;
    if (profile == null || pending == null) {
      _sosPollTimer?.cancel();
      return;
    }
    try {
      final events = await SosRepository(widget.token, profile.id).listMine();
      final matches = events.where((e) => e.id == pending.id);
      final match = matches.isEmpty ? null : matches.first;
      if (match != null && match.acknowledged) {
        _sosPollTimer?.cancel();
        if (!mounted) return;
        setState(() => _pendingSos = null);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Your SOS was acknowledged by your branch.'), backgroundColor: MicrofiColors.secondary),
        );
      }
    } catch (_) {
      // Try again on the next tick.
    }
  }

  @override
  Widget build(BuildContext context) {
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
          Center(child: FilledButton(onPressed: _load, child: const Text('Retry'))),
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
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.add_circle, size: 20, color: Colors.white),
                  SizedBox(width: 8),
                  Text('New Collection', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
                ],
              ),
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Row(
            children: [
              Expanded(child: _QuickAction(icon: Icons.map, label: 'My Route', onTap: _openMyRoute)),
              const SizedBox(width: MicrofiSpacing.gapLg),
              Expanded(child: _QuickAction(icon: Icons.history, label: 'Collection History', onTap: _openHistory)),
            ],
          ),
          const SizedBox(height: MicrofiSpacing.gap),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: _openSponsorActivation,
              icon: const Icon(Icons.how_to_reg, size: 16),
              label: const Text('Sponsor Client Activation', style: TextStyle(fontSize: 13)),
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
              label: const Text('Contact Branch', style: TextStyle(fontSize: 13)),
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
              const Text('Recent Collections', style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
              TextButton(onPressed: _openHistory, child: const Text('See all', style: TextStyle(fontSize: 13))),
            ],
          ),
          const Divider(color: MicrofiColors.outlineVariant, height: 1),
          if (_recent.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 20),
              child: Text('No collections recorded yet.', style: TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
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
                  const Text("TODAY'S COLLECTIONS", style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.4)),
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
                  const Text('Ceiling', style: TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                  Text('${_fmt(escrow.effectiveCeilingXaf)} XAF', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
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
                  ? '${(escrow.utilization * 100).round()}% capacity reached. Deposit soon.'
                  : '${(escrow.utilization * 100).round()}% capacity reached. Safe to continue.',
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
                Text(collection.clientName ?? 'Unknown client', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
                Text('$time • Cash', style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
              ],
            ),
          ),
          Text('+${_fmt(collection.amountXaf)} XAF', style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.secondary)),
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
          Text(active ? 'Online' : 'Suspended', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: active ? MicrofiColors.secondary : MicrofiColors.error)),
        ],
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
      child: const Row(
        children: [
          Icon(Icons.emergency, color: MicrofiColors.onErrorContainer, size: 18),
          SizedBox(width: 8),
          Expanded(
            child: Text(
              'SOS pending — awaiting acknowledgement from your branch.',
              style: TextStyle(fontSize: 12.5, color: MicrofiColors.onErrorContainer, fontWeight: FontWeight.w600),
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
