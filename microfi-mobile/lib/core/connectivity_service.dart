import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:http/http.dart' as http;
import 'api_client.dart';

/// Real online/offline detection — not decorative, and not just `navigator.onLine`.
///
/// `navigator.onLine` (what connectivity_plus reports on web) only reflects whether the device's
/// network interface is associated with a network, e.g. still connected to a Wi-Fi router whose
/// own uplink is down. It does not mean the internet — or this API — is actually reachable, so on
/// its own it under-reports "offline" and can leave the header showing "Online" when nothing will
/// actually load. To get a real signal, every check is confirmed with an actual HTTP round-trip to
/// the API gateway: any response (even a 401) proves reachability; a thrown exception (timeout,
/// connection refused, DNS failure) proves it isn't. `navigator.onLine` is used only as a fast
/// short-circuit for the obvious "interface is down" case, never as proof of "online" by itself.
class ConnectivityService {
  ConnectivityService._();
  static final ConnectivityService instance = ConnectivityService._();

  final Connectivity _connectivity = Connectivity();

  static const _probeTimeout = Duration(seconds: 4);
  static const _pollInterval = Duration(seconds: 12);

  Future<bool> isOnline() async {
    final results = await _connectivity.checkConnectivity();
    if (!_hasInterface(results)) return false;
    return _probeReachable();
  }

  Future<bool> _probeReachable() async {
    try {
      // Any HTTP response — including 401/404 — proves the gateway was actually reached.
      // Deliberately unauthenticated: this only tests reachability, not the caller's session.
      await http.get(Uri.parse('${ApiClient.baseUrl}/agents/me')).timeout(_probeTimeout);
      return true;
    } catch (_) {
      return false;
    }
  }

  bool _hasInterface(List<ConnectivityResult> results) => results.any((r) => r != ConnectivityResult.none);

  StreamController<bool>? _controller;
  StreamSubscription<List<ConnectivityResult>>? _interfaceSub;
  Timer? _pollTimer;
  bool? _lastValue;

  /// Broadcasts only real changes, backed by both interface-change events (fast negative signal)
  /// and periodic reachability polling (catches "interface up, internet actually down").
  Stream<bool> get onOnlineChanged {
    _controller ??= StreamController<bool>.broadcast(
      onListen: _start,
      onCancel: _stop,
    );
    return _controller!.stream;
  }

  void _start() {
    _checkAndEmit();
    _interfaceSub = _connectivity.onConnectivityChanged.listen((_) => _checkAndEmit());
    _pollTimer = Timer.periodic(_pollInterval, (_) => _checkAndEmit());
  }

  void _stop() {
    _interfaceSub?.cancel();
    _interfaceSub = null;
    _pollTimer?.cancel();
    _pollTimer = null;
    _lastValue = null;
  }

  Future<void> _checkAndEmit() async {
    final online = await isOnline();
    if (online != _lastValue) {
      _lastValue = online;
      _controller?.add(online);
    }
  }
}
