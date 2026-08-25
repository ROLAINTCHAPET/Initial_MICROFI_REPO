import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'route_repository.dart';
import '../../l10n/app_localizations.dart';

/// UC-11 — Historical Route Visualization. A map view (Google/Mapbox tiles) is a real feature this
/// design mock implies but this app doesn't integrate a maps SDK yet — this is a list-based
/// stand-in showing the same underlying data (GPS pings + collection markers, chronologically)
/// rather than a fabricated map.
class RouteScreen extends StatefulWidget {
  final String token;

  const RouteScreen({super.key, required this.token});

  @override
  State<RouteScreen> createState() => _RouteScreenState();
}

class _RouteScreenState extends State<RouteScreen> {
  late final RouteRepository _repository = RouteRepository(widget.token);

  AgentRoute? _route;
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
      final route = await _repository.fetchToday();
      if (!mounted) return;
      setState(() => _route = route);
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
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.rtMyRouteTodayTitle)),
      body: SafeArea(child: _buildBody(l10n)),
    );
  }

  Widget _buildBody(AppLocalizations l10n) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_error != null) {
      return ListView(
        padding: const EdgeInsets.all(24),
        children: [
          const SizedBox(height: 60),
          const Icon(Icons.error_outline, color: MicrofiColors.error, size: 40),
          const SizedBox(height: 12),
          Text(_error!, textAlign: TextAlign.center, style: const TextStyle(color: MicrofiColors.error)),
          const SizedBox(height: 16),
          Center(child: FilledButton(onPressed: _load, child: Text(l10n.commonRetry))),
        ],
      );
    }

    final route = _route!;
    final events = <_RouteEvent>[
      ...route.points.map((p) => _RouteEvent(time: p.recordedAt, isCollection: false, lat: p.lat, lon: p.lon)),
      ...route.transactions.map((t) => _RouteEvent(time: t.collectedAt, isCollection: true, lat: t.lat, lon: t.lon, amountXaf: t.amountXaf)),
    ]..sort((a, b) => a.time.compareTo(b.time));

    if (events.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            const SizedBox(height: 80),
            const Icon(Icons.map_outlined, color: MicrofiColors.outlineVariant, size: 48),
            const SizedBox(height: 12),
            Center(child: Text(l10n.rtNoGpsOrCollections, style: const TextStyle(color: MicrofiColors.onSurfaceVariant))),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        padding: const EdgeInsets.all(MicrofiSpacing.page),
        itemCount: events.length,
        separatorBuilder: (_, _) => const SizedBox(height: 2),
        itemBuilder: (context, index) {
          final e = events[index];
          final time = TimeOfDay.fromDateTime(e.time.toLocal()).format(context);
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Column(
                children: [
                  Icon(
                    e.isCollection ? Icons.payments : Icons.circle,
                    size: e.isCollection ? 16 : 8,
                    color: e.isCollection ? MicrofiColors.secondary : MicrofiColors.outlineVariant,
                  ),
                  Container(width: 2, height: 32, color: MicrofiColors.outlineVariant),
                ],
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        e.isCollection ? l10n.rtCollectionLine(_fmt(e.amountXaf!)) : l10n.rtGpsPing,
                        style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: e.isCollection ? MicrofiColors.secondary : MicrofiColors.onSurface),
                      ),
                      Text(l10n.rtTimeLatLonLine(time, e.lat.toStringAsFixed(4), e.lon.toStringAsFixed(4)), style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant)),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
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

class _RouteEvent {
  final DateTime time;
  final bool isCollection;
  final double lat;
  final double lon;
  final int? amountXaf;

  _RouteEvent({required this.time, required this.isCollection, required this.lat, required this.lon, this.amountXaf});
}
