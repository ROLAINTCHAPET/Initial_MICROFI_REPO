import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:geolocator/geolocator.dart';
import 'package:uuid/uuid.dart';
import '../../core/api_client.dart';
import '../../core/collection_chain_codec.dart';
import '../../core/connectivity_service.dart';
import '../../core/design_tokens.dart';
import '../../core/device_id_service.dart';
import '../../core/geofence_math.dart';
import '../../core/installation_id_service.dart';
import '../../core/local_ceiling_cache.dart';
import '../../core/local_collection_chain_cache.dart';
import '../../core/local_geofence_cache.dart';
import '../../core/local_pin_verifier.dart';
import '../../core/local_schedule_cache.dart';
import '../../core/local_trusted_time_cache.dart';
import '../../core/locale_preference.dart';
import '../../core/schedule_window.dart';
import '../../core/location.dart';
import '../../core/offline_receipt_composer.dart';
import '../../core/printer_service.dart';
import '../../core/qr_receipt_signer.dart';
import '../../core/receipt_context_cache.dart';
import '../../core/receipt_file_service.dart';
import 'receipt_qr_screen.dart';
import '../../core/status_components.dart';
import '../home/agent_profile.dart';
import '../home/branch_repository.dart';
import '../home/contact_branch.dart';
import '../home/home_repository.dart';
import 'client.dart';
import 'client_repository.dart';
import 'collection_repository.dart';
import 'notification_repository.dart';
import 'offline_queue_repository.dart';
import 'receipt_models.dart';
import '../../l10n/app_localizations.dart';

const List<int> _denominations = [10000, 5000, 2000, 1000, 500, 200, 100, 50, 25];

class _ChainFields {
  final String installationId;
  final int counter;
  final String previousHash;
  final String currentHash;
  final String signature;

  _ChainFields({required this.installationId, required this.counter, required this.previousHash, required this.currentHash, required this.signature});
}

/// Graphical Design/agent/collection_denominations — the 3-step Collection Wizard: find client,
/// count denominations (with a live daily-ceiling preview), confirm and submit.
class CollectionStepperScreen extends StatefulWidget {
  final String token;
  final AgentProfile profile;

  const CollectionStepperScreen({super.key, required this.token, required this.profile});

  /// Kept as a convenience getter (not a stored field) so the ~7 existing internal uses of
  /// widget.agentId didn't need touching when this changed from a bare id to the full profile —
  /// needed now for employeeCode/fullName on an offline-composed receipt (see
  /// OfflineReceiptComposer), which a bare id can't provide without a network round-trip.
  String get agentId => profile.id;

  @override
  State<CollectionStepperScreen> createState() => _CollectionStepperScreenState();
}

class _CollectionStepperScreenState extends State<CollectionStepperScreen> {
  late final ClientRepository _clientRepository = ClientRepository(widget.token);
  late final CollectionRepository _collectionRepository = CollectionRepository(widget.token);
  late final HomeRepository _homeRepository = HomeRepository(widget.token);
  late final NotificationRepository _notificationRepository = NotificationRepository(widget.token);
  final PrinterService _printerService = PrinterService();
  final ReceiptFileService _receiptFileService = ReceiptFileService();

  int _step = 0;

  // Step 1 — client search
  final _searchController = TextEditingController();
  Timer? _debounce;
  List<Client> _searchResults = [];
  bool _searching = false;
  String? _searchError;

  // Step 2 — denominations + GPS + live ceiling preview
  Client? _client;
  final Map<int, int> _counts = {for (final d in _denominations) d: 0};
  Position? _position;
  String? _locationError;
  bool _locating = false;
  EscrowStatus? _escrow;
  CeilingSnapshot? _cachedCeiling;
  int _queuedTodayXaf = 0;
  AgentGeofence? _geofence;
  GeofenceSnapshot? _cachedGeofence;
  AgentBranch? _branch;
  ScheduleSnapshot? _cachedSchedule;

  // Step 3 — confirm/submit
  final _pinController = TextEditingController();
  bool _submitting = false;
  String? _submitError;
  CollectionResult? _result;
  bool _queuedOffline = false;
  bool _clockRolledBackWarning = false;
  String? _receiptText;
  ReceiptData? _receiptData;
  /// Set only for an offline-queued collection — there's no server-confirmed CollectionResult.id
  /// to key a filename off yet.
  String? _offlineDeviceTxId;
  QrReceiptPayload? _qrPayload;
  bool _printing = false;
  bool _printed = false;
  String? _printError;
  bool _downloading = false;
  bool _downloaded = false;
  String? _downloadError;

  @override
  void initState() {
    super.initState();
    _search();
    _searchController.addListener(_onQueryChanged);
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _searchController.removeListener(_onQueryChanged);
    _searchController.dispose();
    _pinController.dispose();
    super.dispose();
  }

  void _onQueryChanged() {
    setState(() {});
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), _search);
  }

  Future<void> _search() async {
    setState(() {
      _searching = true;
      _searchError = null;
    });
    try {
      final results = await _clientRepository.search(_searchController.text);
      if (!mounted) return;
      setState(() => _searchResults = results);
    } on ApiException catch (e) {
      if (!mounted) return;
      setState(() => _searchError = e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _searchError = AppLocalizations.of(context)!.errorUnableToReachServerShort);
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  void _selectClient(Client client) {
    setState(() {
      _client = client;
      _step = 1;
    });
    _captureLocation();
    _loadEscrow();
  }

  Future<void> _captureLocation() async {
    setState(() {
      _locating = true;
      _locationError = null;
    });
    try {
      final position = await captureCurrentLocation();
      if (!mounted) return;
      setState(() => _position = position);
    } on LocationUnavailable catch (e) {
      if (!mounted) return;
      setState(() => _locationError = e.message(AppLocalizations.of(context)!));
    } catch (e) {
      if (!mounted) return;
      setState(() => _locationError = e.toString());
    } finally {
      if (mounted) setState(() => _locating = false);
    }
  }

  Future<void> _contactBranch() => contactBranch(context, widget.token);

  Future<void> _loadEscrow() async {
    try {
      final escrow = await _homeRepository.fetchEscrow(widget.agentId);
      if (!mounted) return;
      setState(() => _escrow = escrow);
      LocalCeilingCache(widget.agentId).save(effectiveCeilingXaf: escrow.effectiveCeilingXaf, cumulativeTodayXaf: escrow.cumulativeTodayXaf);
      LocalTrustedTimeCache(widget.agentId).recordSuccessfulServerContact();
    } catch (_) {
      // No connectivity (or a transient failure) — fall back to the last snapshot the server
      // actually confirmed, so the offline path still has something to check against instead of
      // flying blind until sync.
      final cached = await LocalCeilingCache(widget.agentId).read();
      if (!mounted) return;
      setState(() => _cachedCeiling = cached);
    }
    await _refreshQueuedTodayTotal();
    await _loadGeofence();
    await _loadSchedule();
  }

  /// Same live-fetch/cached-fallback pattern as escrow, just for the assigned geofence polygon —
  /// refreshed here (not only on Home load) so the check right before an offline submit uses the
  /// freshest snapshot this screen session can get.
  Future<void> _loadGeofence() async {
    try {
      final geofence = await _homeRepository.fetchGeofence();
      if (!mounted) return;
      setState(() => _geofence = geofence);
      LocalGeofenceCache(widget.agentId).save(geofence);
    } catch (_) {
      final cached = await LocalGeofenceCache(widget.agentId).read();
      if (!mounted) return;
      setState(() => _cachedGeofence = cached);
    }
  }

  /// Same live-fetch/cached-fallback pattern again, for the branch's open/close hours — so an
  /// offline collection outside business hours can be caught here, before a receipt is handed
  /// over, instead of only at sync (see AgentDirectoryService#requireWithinScheduleWindow).
  Future<void> _loadSchedule() async {
    try {
      final branch = await BranchRepository(widget.token).fetchMyBranch();
      if (!mounted) return;
      setState(() => _branch = branch);
      LocalScheduleCache(widget.agentId).save(openTime: branch.openTime, closeTime: branch.closeTime);
    } catch (_) {
      final cached = await LocalScheduleCache(widget.agentId).read();
      if (!mounted) return;
      setState(() => _cachedSchedule = cached);
    }
  }

  /// Neither a live escrow fetch nor the cached snapshot reflects collections still sitting in the
  /// offline queue (the server hasn't seen them yet either way) — so both paths need this added on
  /// top to get a realistic projected total.
  Future<void> _refreshQueuedTodayTotal() async {
    final pending = await OfflineQueueRepository(widget.agentId).list();
    final now = DateTime.now().toUtc();
    final total = pending
        .where((c) {
          final collectedAt = DateTime.tryParse(c.collectedAtIso)?.toUtc();
          return collectedAt != null && collectedAt.year == now.year && collectedAt.month == now.month && collectedAt.day == now.day;
        })
        .fold(0, (sum, c) => sum + c.amountXaf);
    if (!mounted) return;
    setState(() => _queuedTodayXaf = total);
  }

  /// Ceiling, projected total, and whether it would exceed — shared by the live preview card and
  /// the offline submit gate so the two can never silently disagree. hasInfo false means neither a
  /// live fetch nor a cached snapshot has ever succeeded (a fresh install's very first collection,
  /// offline, before Home has loaded once) — there's nothing to check against yet, so this doesn't
  /// block; the server still catches it at sync, same as before this feature existed.
  ({int ceiling, int projected, bool wouldExceed, bool hasInfo}) _ceilingCheck() {
    final ceiling = _escrow?.effectiveCeilingXaf ?? _cachedCeiling?.effectiveCeilingXaf ?? 0;
    final baseline = _escrow?.cumulativeTodayXaf ?? (_cachedCeiling?.cumulativeIsFromToday == true ? _cachedCeiling!.cumulativeTodayXaf : 0);
    final hasInfo = _escrow != null || _cachedCeiling != null;
    final projected = baseline + _queuedTodayXaf + _total;
    return (ceiling: ceiling, projected: projected, wouldExceed: hasInfo && ceiling > 0 && projected > ceiling, hasInfo: hasInfo);
  }

  /// Same hasInfo reasoning as [_ceilingCheck]: no live fetch nor cached snapshot ever succeeding
  /// means this doesn't block — the server still catches it at sync, same as before this feature
  /// existed. Empty vertices means the agent has no geofence assigned at all (server's own
  /// "unassigned = unrestricted" default via GeofenceService#isWithinAssignedGeofence) — that's
  /// hasInfo:true (we DO know their status: unrestricted), not "no info."
  ({bool isOutsideZone, bool hasInfo}) _geofenceCheck() {
    final vertices = _geofence?.vertices ?? _cachedGeofence?.vertices;
    if (vertices == null || _position == null) {
      return (isOutsideZone: false, hasInfo: false);
    }
    if (vertices.isEmpty) {
      return (isOutsideZone: false, hasInfo: true);
    }
    final inside = isInsidePolygon(_position!.latitude, _position!.longitude, vertices);
    return (isOutsideZone: !inside, hasInfo: true);
  }

  /// Same hasInfo reasoning as [_ceilingCheck]/[_geofenceCheck]: no live fetch nor cached snapshot
  /// ever succeeding means this doesn't block — the server still catches it at sync. A branch with
  /// no configured hours at all (openTime/closeTime both null) is hasInfo:true and never outside
  /// the window, mirroring the server's own "no schedule configured = unrestricted" default.
  ({bool isOutsideWindow, bool hasInfo}) _scheduleCheck() {
    final openTime = _branch?.openTime ?? _cachedSchedule?.openTime;
    final closeTime = _branch?.closeTime ?? _cachedSchedule?.closeTime;
    final hasInfo = _branch != null || _cachedSchedule != null;
    if (!hasInfo) {
      return (isOutsideWindow: false, hasInfo: false);
    }
    return (isOutsideWindow: !isWithinScheduleWindow(openTime, closeTime, DateTime.now()), hasInfo: true);
  }

  /// Offline Field Collection Security Algorithm v1.1 §11: a flag, not a hard block — the server
  /// remains the real enforcement boundary for the daily ceiling regardless of what this says.
  /// Only meaningful once at least one successful server contact has ever anchored it.
  Future<bool> _clockRolledBack() => LocalTrustedTimeCache(widget.agentId).hasClockRolledBackSinceLastServerContact();

  /// Offline Field Collection Security Algorithm v1.1 §2/§5 hash-chain — builds the fields the
  /// server's CollectionService#applyChainRules verifies, using this installation's own advancing
  /// local state (LocalCollectionChainCache). Null when this installation has no HMAC secret yet
  /// (a pre-chain app build's last login) — the request is then sent with no chain fields at all,
  /// which the server treats as back-compat rule 0 rather than a validation failure.
  Future<_ChainFields?> _buildChainFields({
    required String deviceTxId,
    required String clientId,
    required int amountXaf,
    required double lat,
    required double lon,
    required DateTime collectedAt,
  }) async {
    final installationId = await InstallationIdService().getInstallationId();
    final secret = await InstallationIdService().readSecret();
    if (secret == null) return null;

    final link = await LocalCollectionChainCache(installationId).nextLink();
    final hash = CollectionChainCodec.computeHash(
      installationId: installationId,
      deviceTxId: deviceTxId,
      clientId: clientId,
      amountXaf: amountXaf,
      lat: lat,
      lon: lon,
      collectedAtEpochMilli: collectedAt.millisecondsSinceEpoch,
      counter: link.counter,
      previousHash: link.previousHash,
    );
    final signature = CollectionChainCodec.computeSignature(installationSecretBase64: secret, currentHash: hash);
    return _ChainFields(installationId: installationId, counter: link.counter, previousHash: link.previousHash, currentHash: hash, signature: signature);
  }

  int get _total => _denominations.fold(0, (sum, d) => sum + d * (_counts[d] ?? 0));

  Future<void> _submit() async {
    if (_total <= 0 || _position == null || _client == null) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _submitting = true;
      _submitError = null;
    });

    if (_pinController.text.isEmpty) {
      setState(() {
        _submitError = l10n.csPinRequiredError;
        _submitting = false;
      });
      return;
    }

    final deviceTxId = const Uuid().v4();
    final terminalId = await DeviceIdService().getDeviceId();
    final lines = _denominations
        .where((d) => (_counts[d] ?? 0) > 0)
        .map((d) => DenominationLine(faceValueXaf: d, quantity: _counts[d]!))
        .toList();
    // Captured once and reused for both the request body and the hash-chain computation below —
    // the server verifies currentHash against exactly the collectedAt it receives, so the two
    // must never be computed from two slightly different DateTime.now() calls.
    final collectedAt = DateTime.now().toUtc();
    final chainFields = await _buildChainFields(
      deviceTxId: deviceTxId,
      clientId: _client!.id,
      amountXaf: _total,
      lat: _position!.latitude,
      lon: _position!.longitude,
      collectedAt: collectedAt,
    );

    final online = await ConnectivityService.instance.isOnline();
    if (!online) {
      // Checked locally before anything is queued or handed to the client — a wrong PIN caught
      // here costs a retry; a wrong PIN only caught at sync means the client already has a
      // receipt for a collection the server is about to reject. See LocalPinVerifier.
      final pinVerifier = LocalPinVerifier(widget.agentId);
      final lockout = await pinVerifier.lockoutRemaining();
      if (lockout != null) {
        setState(() {
          _submitError = l10n.csTooManyPinAttempts(lockout.inMinutes + 1);
          _submitting = false;
        });
        return;
      }
      if (!await pinVerifier.verify(_pinController.text)) {
        setState(() {
          _submitError = l10n.csIncorrectPin;
          _submitting = false;
        });
        return;
      }

      // Same reasoning as the PIN check above, and same order the server itself checks in
      // (AgentDirectoryService#requireWithinScheduleWindow runs before the geofence gate in
      // CollectionService#recordCollection) — blocked here, before the client is handed a
      // receipt, using the last schedule the server actually confirmed (see LocalScheduleCache).
      final scheduleCheck = _scheduleCheck();
      if (scheduleCheck.hasInfo && scheduleCheck.isOutsideWindow) {
        setState(() {
          _submitError = l10n.csScheduleBlockedOfflineMessage;
          _submitting = false;
        });
        return;
      }

      // Same reasoning as the PIN check above: blocked here, before the client is handed a
      // receipt, using the last geofence the server actually confirmed (see LocalGeofenceCache)
      // — not after the fact at sync, when the cash is already collected and there's nothing left
      // to undo. Placed before the ceiling check since "are you even allowed to be here" is the
      // more fundamental gate.
      final geofenceCheck = _geofenceCheck();
      if (geofenceCheck.hasInfo && geofenceCheck.isOutsideZone) {
        setState(() {
          _submitError = l10n.csGeofenceBlockedOfflineMessage;
          _submitting = false;
        });
        return;
      }

      // Same reasoning as the PIN check above, applied to BR-03: blocked here, before the client
      // is handed a receipt, using the last ceiling/cumulative the server actually confirmed (see
      // LocalCeilingCache) — not after the fact at sync, when the cash is already collected.
      final check = _ceilingCheck();
      if (check.hasInfo && check.wouldExceed) {
        setState(() {
          _submitError = l10n.csCeilingExceedOfflineMessage(_fmt(check.projected), _fmt(check.ceiling));
          _submitting = false;
        });
        return;
      }

      // Offline Field Collection Security Algorithm v1.1 §11 — flag, not a hard block, a clock
      // that appears to have moved backward since the last time this device actually talked to
      // the server. The server remains the real enforcement boundary for the daily ceiling either
      // way; this only annotates the submit for the eventual sync-time review.
      final clockRolledBack = await _clockRolledBack();

      // The PIN entered just now travels with the queued item (encrypted, see
      // OfflineQueueRepository) so sync can upload it the moment connectivity returns with no
      // second prompt — this is the same one-time PIN confirmation an online collection already
      // requires, not a weaker check.
      await OfflineQueueRepository(widget.agentId).add(PendingCollection(
        deviceTxId: deviceTxId,
        clientId: _client!.id,
        clientName: _client!.fullName,
        amountXaf: _total,
        lat: _position!.latitude,
        lon: _position!.longitude,
        accuracyM: _position!.accuracy,
        collectedAtIso: collectedAt.toIso8601String(),
        denominationLines: lines,
        pin: _pinController.text,
        terminalId: terminalId,
        installationId: chainFields?.installationId,
        collectionCounter: chainFields?.counter,
        previousHash: chainFields?.previousHash,
        currentHash: chainFields?.currentHash,
        signature: chainFields?.signature,
      ));
      // An offline-queued item still advances what this installation believes it has recorded —
      // the server verifies/catches any divergence at sync time (CollectionService#applyChainRules),
      // it doesn't need this local state to be provisional.
      if (chainFields != null) {
        await LocalCollectionChainCache(chainFields.installationId).save(lastCounter: chainFields.counter, lastHash: chainFields.currentHash);
      }
      await _refreshQueuedTodayTotal();
      _offlineDeviceTxId = deviceTxId;
      await _composeReceiptLocally(deviceTxId: deviceTxId, collectedAt: collectedAt, lines: lines);
      await _composeQrPayload(uniqueRef: deviceTxId, collectedAt: collectedAt, lines: lines);
      if (!mounted) return;
      setState(() {
        _queuedOffline = true;
        _clockRolledBackWarning = clockRolledBack;
        _submitting = false;
      });
      return;
    }

    try {
      final result = await _collectionRepository.record(
        clientId: _client!.id,
        amountXaf: _total,
        lat: _position!.latitude,
        lon: _position!.longitude,
        accuracyM: _position!.accuracy,
        deviceTxId: deviceTxId,
        terminalId: terminalId,
        denominationLines: lines,
        pin: _pinController.text,
        collectedAt: collectedAt,
        installationId: chainFields?.installationId,
        collectionCounter: chainFields?.counter,
        previousHash: chainFields?.previousHash,
        currentHash: chainFields?.currentHash,
        signature: chainFields?.signature,
      );
      if (!mounted) return;
      setState(() => _result = result);
      LocalPinVerifier(widget.agentId).seed(_pinController.text);
      LocalTrustedTimeCache(widget.agentId).recordSuccessfulServerContact();
      if (chainFields != null) {
        LocalCollectionChainCache(chainFields.installationId).save(lastCounter: chainFields.counter, lastHash: chainFields.currentHash);
      }
      // Composed immediately, not awaited on the server's own response below — see
      // _composeReceiptLocally's doc for why this is safe and byte-for-byte identical either way.
      _composeReceiptLocally(deviceTxId: deviceTxId, collectedAt: collectedAt, lines: lines);
      _notifyAfterSuccess(result.id);
      _composeQrPayload(uniqueRef: result.id, collectedAt: collectedAt, lines: lines);
    } on ApiException catch (e) {
      setState(() => _submitError = e.message);
    } catch (_) {
      setState(() => _submitError = l10n.errorUnableToReachServerShort);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  /// UC-09: fires the Flash SMS attempt as soon as the collection succeeds — best-effort, since an
  /// SMS-gateway failure (e.g. no carrier credentials configured yet) must never look like a
  /// problem with the collection itself, which already succeeded. Its response also carries a
  /// server-composed receiptText/receiptData, but that's no longer used here: _composeReceiptLocally
  /// already renders the identical receipt immediately, without waiting on this network round trip.
  Future<void> _notifyAfterSuccess(String collectionId) async {
    try {
      await _notificationRepository.notifyCollection(collectionId, printedReceipt: false);
    } catch (_) {
      // Silent — SMS delivery status isn't something the agent needs to act on here; the
      // Back-Office notification audit log is the place to investigate a failed send.
    }
  }

  Future<void> _printReceipt() async {
    final receiptText = _receiptText;
    if (receiptText == null) return;
    final l10n = AppLocalizations.of(context)!;
    setState(() {
      _printing = true;
      _printError = null;
    });
    try {
      var printer = await _printerService.savedPrinter();
      if (printer == null) {
        if (!mounted) return;
        printer = await _pickPrinter();
        if (printer == null) {
          setState(() => _printing = false);
          return;
        }
        await _printerService.savePrinter(printer);
      }
      final success = await _printerService.printReceipt(receiptText, printer: printer);
      if (!success) {
        throw PrinterUnavailable(PrinterUnavailableReason.didNotConfirm);
      }
      if (_result != null) {
        await _notificationRepository.notifyCollection(_result!.id, printedReceipt: true);
      }
      if (!mounted) return;
      setState(() => _printed = true);
    } on PrinterUnavailable catch (e) {
      if (!mounted) return;
      setState(() => _printError = e.message(l10n));
    } catch (_) {
      if (!mounted) return;
      setState(() => _printError = l10n.csCouldNotPrintReceipt);
    } finally {
      if (mounted) setState(() => _printing = false);
    }
  }

  /// Renders the styled receipt as a PDF and opens it immediately — no printer needed, and no
  /// share sheet — so the agent has proof on the device alongside (or instead of) the physical slip.
  /// fileNameHint falls back to deviceTxId for an offline-queued collection, which has no
  /// server-confirmed collection id yet — same purpose, just whichever
  /// unique reference actually exists at this point.
  Future<void> _downloadReceipt() async {
    final receiptData = _receiptData;
    if (receiptData == null) return;
    final l10n = AppLocalizations.of(context)!;
    final fileNameHint = _result?.id.substring(0, 8) ?? _offlineDeviceTxId ?? 'receipt';
    setState(() {
      _downloading = true;
      _downloadError = null;
    });
    try {
      await _receiptFileService.downloadReceipt(receiptData, fileNameHint: fileNameHint, l10n: l10n);
      if (!mounted) return;
      setState(() => _downloaded = true);
    } on ReceiptFileDownloadFailed catch (e) {
      if (!mounted) return;
      setState(() {
        _downloaded = true;
        _downloadError = l10n.csReceiptSavedCouldNotOpen(e.message);
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _downloadError = l10n.csCouldNotDownloadReceipt);
    } finally {
      if (mounted) setState(() => _downloading = false);
    }
  }

  Future<PairedPrinter?> _pickPrinter() async {
    final l10n = AppLocalizations.of(context)!;
    if (!await _printerService.isReady()) {
      setState(() => _printError = l10n.csBluetoothPermissionNeeded);
      return null;
    }
    final printers = await _printerService.pairedPrinters();
    if (!mounted) return null;
    if (printers.isEmpty) {
      setState(() => _printError = l10n.csNoPairedPrinterFound);
      return null;
    }
    return showDialog<PairedPrinter>(
      context: context,
      builder: (context) => SimpleDialog(
        title: Text(l10n.csSelectThermalPrinter),
        children: printers
            .map((p) => SimpleDialogOption(
                  onPressed: () => Navigator.of(context).pop(p),
                  child: Text(p.name.isEmpty ? p.macAddress : p.name),
                ))
            .toList(),
      ),
    );
  }

  void _back() {
    if (_step == 0) {
      Navigator.of(context).pop(false);
    } else {
      setState(() => _step -= 1);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: _back),
          title: Text(l10n.csCollectCashTitle),
        ),
        body: SafeArea(
          child: Column(
            children: [
              if (_result == null && !_queuedOffline) _StepperHeader(step: _step),
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 250),
                  transitionBuilder: (child, animation) => FadeTransition(
                    opacity: animation,
                    child: SlideTransition(
                      position: Tween<Offset>(begin: const Offset(0.04, 0), end: Offset.zero).animate(animation),
                      child: child,
                    ),
                  ),
                  child: KeyedSubtree(key: ValueKey(_stepKey), child: _buildStep()),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Distinguishes every screen AnimatedSwitcher above needs to tell apart — success/queued are
  /// terminal states outside the 0-2 step count, so they can't collide with a real step number.
  String get _stepKey => _result != null ? 'success' : (_queuedOffline ? 'queued' : 'step-$_step');

  Widget _buildStep() {
    if (_result != null) return _buildSuccess();
    if (_queuedOffline) return _buildQueuedOffline();
    switch (_step) {
      case 1:
        return _buildDenominationsStep();
      case 2:
        return _buildConfirmStep();
      case 0:
      default:
        return _buildClientStep();
    }
  }

  Widget _buildClientStep() {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            controller: _searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) {
              _debounce?.cancel();
              _search();
            },
            decoration: InputDecoration(
              hintText: l10n.csSearchHint,
              prefixIcon: const Icon(Icons.search),
              suffixIcon: _searchController.text.isEmpty ? null : IconButton(icon: const Icon(Icons.clear), onPressed: _searchController.clear),
            ),
          ),
        ),
        if (_searching) const Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator()),
        if (_searchError != null)
          Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Text(_searchError!, style: const TextStyle(color: MicrofiColors.error))),
        if (!_searching && _searchResults.isEmpty && _searchError == null)
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              _searchController.text.trim().isEmpty ? l10n.csNoClientsRegistered : l10n.csNoClientsFound,
              style: const TextStyle(color: MicrofiColors.onSurfaceVariant),
            ),
          ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: _searchResults.length,
            itemBuilder: (context, index) {
              final c = _searchResults[index];
              return Container(
                margin: const EdgeInsets.only(bottom: 10),
                decoration: BoxDecoration(
                  color: MicrofiColors.surfaceContainerLowest,
                  borderRadius: BorderRadius.circular(MicrofiRadius.md),
                  boxShadow: MicrofiShadows.softSmall,
                ),
                child: ListTile(
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.md)),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                  leading: Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(color: MicrofiColors.primary.withValues(alpha: 0.08), shape: BoxShape.circle),
                    child: const Icon(Icons.person_rounded, color: MicrofiColors.primary, size: 20),
                  ),
                  title: Text(c.fullName, style: const TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text(l10n.csClientSubtitle(c.mfiMemberNo, c.phone)),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => _selectClient(c),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildDenominationsStep() {
    final l10n = AppLocalizations.of(context)!;
    final client = _client!;
    final canContinue = _total > 0 && _position != null;
    final check = _ceilingCheck();
    final projected = check.projected;
    final ceiling = check.ceiling;
    final wouldExceed = check.wouldExceed;

    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        _ClientContextCard(client: client),
        const SizedBox(height: 12),
        _Card(
          child: Row(
            children: [
              Icon(
                _locating ? Icons.location_searching : (_position != null ? Icons.location_on : Icons.location_off),
                color: _position != null ? MicrofiColors.secondary : (_locationError != null ? MicrofiColors.error : MicrofiColors.onSurfaceVariant),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  _locating
                      ? l10n.csCapturingGps
                      : _position != null
                          ? l10n.csLocationCaptured(_position!.accuracy.round())
                          : (_locationError ?? l10n.csLocationUnavailable),
                  style: TextStyle(color: _locationError != null ? MicrofiColors.error : MicrofiColors.onSurfaceVariant),
                ),
              ),
              if (!_locating && _position == null) TextButton(onPressed: _captureLocation, child: Text(l10n.commonRetry)),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Container(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: MicrofiColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(MicrofiRadius.lg),
            boxShadow: MicrofiShadows.soft,
          ),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(14),
                decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: MicrofiColors.outlineVariant, width: 0.75))),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(l10n.csDenominationBreakdown, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                    Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(color: MicrofiColors.secondary.withValues(alpha: 0.1), shape: BoxShape.circle),
                      child: const Icon(Icons.payments_rounded, color: MicrofiColors.secondary, size: 16),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Column(
                  children: _denominations
                      .map((d) => _DenominationRow(faceValue: d, count: _counts[d] ?? 0, onChanged: (v) => setState(() => _counts[d] = v)))
                      .toList(),
                ),
              ),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: const BoxDecoration(color: MicrofiColors.surfaceContainer),
                child: Column(
                  children: [
                    Text(l10n.csCalculatedTotal, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.5)),
                    const SizedBox(height: 6),
                    Text(l10n.amountXaf(_fmt(_total)), style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, color: MicrofiColors.primary)),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (check.hasInfo) ...[
          const SizedBox(height: 12),
          if (wouldExceed)
            EscrowCeilingReachedCard(
              message: l10n.csEscrowExceedMessage(_fmt(projected), _fmt(ceiling), _fmt(projected - ceiling)),
              onContactBranch: _contactBranch,
            )
          else
            _CeilingPreviewBar(projected: projected, ceiling: ceiling),
        ],
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: FilledButton.icon(
            onPressed: canContinue ? () => setState(() => _step = 2) : null,
            icon: const Icon(Icons.arrow_forward),
            label: Text(l10n.csVerifyAndContinue),
            style: FilledButton.styleFrom(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.full))),
          ),
        ),
      ],
    );
  }

  Widget _buildConfirmStep() {
    final l10n = AppLocalizations.of(context)!;
    final client = _client!;
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        _ClientContextCard(client: client),
        const SizedBox(height: 12),
        _Card(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(l10n.csConfirmCollectionTitle, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
              const SizedBox(height: 12),
              ..._denominations.where((d) => (_counts[d] ?? 0) > 0).map((d) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(l10n.csDenominationLine(d, _counts[d] ?? 0), style: const TextStyle(color: MicrofiColors.onSurfaceVariant)),
                        Text(l10n.amountXaf(_fmt(d * (_counts[d] ?? 0))), style: const TextStyle(fontWeight: FontWeight.w600)),
                      ],
                    ),
                  )),
              const Divider(color: MicrofiColors.outlineVariant, height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(l10n.commonTotal, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                  Text(l10n.amountXaf(_fmt(_total)), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                l10n.csLocationLine(_position!.latitude.toStringAsFixed(5), _position!.longitude.toStringAsFixed(5), _position!.accuracy.round()),
                style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _Card(
          child: TextField(
            controller: _pinController,
            decoration: InputDecoration(
              labelText: l10n.csEnterPinToConfirm,
              prefixIcon: const Icon(Icons.lock_outline),
              border: InputBorder.none,
            ),
            obscureText: true,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          ),
        ),
        if (_submitError != null) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: MicrofiColors.errorContainer,
              borderRadius: BorderRadius.circular(MicrofiRadius.md),
              boxShadow: MicrofiShadows.softSmall,
            ),
            child: Row(
              children: [
                const Icon(Icons.error_outline_rounded, color: MicrofiColors.onErrorContainer, size: 18),
                const SizedBox(width: 8),
                Expanded(child: Text(_submitError!, style: const TextStyle(color: MicrofiColors.onErrorContainer))),
              ],
            ),
          ),
        ],
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: FilledButton(
            onPressed: _submitting ? null : _submit,
            child: _submitting
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : Text(l10n.csRecordCollection),
          ),
        ),
      ],
    );
  }

  Widget _buildSuccess() {
    final l10n = AppLocalizations.of(context)!;
    final result = _result!;
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        const SizedBox(height: 20),
        SuccessCard(
          title: result.duplicate ? l10n.csAlreadyRecorded : l10n.csCollectionValidated,
          subtitle: result.locationName != null
              ? l10n.csTransactionIdWithLocation(result.id.substring(0, 8).toUpperCase(), result.locationName!)
              : l10n.csTransactionId(result.id.substring(0, 8).toUpperCase()),
          amountLabel: l10n.csAmountCollected,
          amountValue: l10n.amountXaf(_fmt(result.amountXaf)),
        ),
        ..._receiptActionWidgets(),
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.commonDone),
          ),
        ),
      ],
    );
  }

  /// Shared by the online success screen and the offline-queued screen — a wrong PIN or a missing
  /// printer looks and behaves identically either way; only how _receiptText/_receiptData got
  /// populated differs (server-composed vs. OfflineReceiptComposer).
  List<Widget> _receiptActionWidgets() {
    final l10n = AppLocalizations.of(context)!;
    return [
      if (_receiptText != null) ...[
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: OutlinedButton.icon(
            onPressed: _printing || _printed ? null : _printReceipt,
            icon: Icon(_printed ? Icons.check_circle : Icons.print, size: 18),
            label: Text(_printed ? l10n.csReceiptPrinted : (_printing ? l10n.csPrinting : l10n.csPrintReceipt)),
          ),
        ),
        if (_printError != null)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(_printError!, style: const TextStyle(color: MicrofiColors.error, fontSize: 12)),
          ),
      ],
      if (_receiptData != null) ...[
        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: OutlinedButton.icon(
            onPressed: _downloading ? null : _downloadReceipt,
            icon: Icon(_downloaded ? Icons.check_circle : Icons.download, size: 18),
            label: Text(_downloaded ? l10n.csReceiptDownloaded : (_downloading ? l10n.csDownloading : l10n.csDownloadReceipt)),
          ),
        ),
        if (_downloadError != null)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Text(_downloadError!, style: const TextStyle(color: MicrofiColors.error, fontSize: 12)),
          ),
      ],
      if (_qrPayload != null) ...[
        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: OutlinedButton.icon(
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ReceiptQrScreen(payload: _qrPayload!))),
            icon: const Icon(Icons.qr_code_2, size: 18),
            label: Text(l10n.csShowQrForClient),
          ),
        ),
      ],
    ];
  }

  /// Builds the same receipt (printable text + structured PDF data) the server's own
  /// POST /collections/{id}/notify would return, using only what's already known locally — no
  /// network round trip. Safe for the ONLINE path too, not just offline: the backend's own
  /// ReceiptTemplateComposer keys the printed "Unique Ref" off deviceTxId, not the server-assigned
  /// collection id, so a locally-composed receipt is byte-for-byte identical to the server's
  /// either way (see OfflineReceiptComposer's doc). Waiting on notifyCollection's response just to
  /// populate this was a real, user-visible delay before Print/Download/QR appeared — composing it
  /// immediately here removes that wait entirely instead of just racing it.
  /// Best-effort: a receipt that fails to compose still leaves the collection safely
  /// recorded/queued — this never blocks or fails the collection itself.
  Future<void> _composeReceiptLocally({
    required String deviceTxId,
    required DateTime collectedAt,
    required List<DenominationLine> lines,
  }) async {
    try {
      final context = await ReceiptContextCache().read();
      final mfiName = context?.mfiName ?? 'MICROFI';
      final branchName = context?.branchName ?? '—';
      final french = LocalePreference.instance.notifier.value.languageCode == 'fr';
      final text = OfflineReceiptComposer.composeText(
        mfiName: mfiName,
        branchName: branchName,
        collectedAtUtc: collectedAt,
        employeeCode: widget.profile.employeeCode,
        agentFullName: widget.profile.fullName,
        clientMemberNo: _client!.mfiMemberNo,
        clientFullName: _client!.fullName,
        amountXaf: _total,
        denominationLines: lines,
        deviceTxId: deviceTxId,
        french: french,
      );
      final data = OfflineReceiptComposer.composeData(
        branchName: branchName,
        collectedAtUtc: collectedAt,
        employeeCode: widget.profile.employeeCode,
        agentFullName: widget.profile.fullName,
        clientMemberNo: _client!.mfiMemberNo,
        clientFullName: _client!.fullName,
        amountXaf: _total,
        denominationLines: lines,
        deviceTxId: deviceTxId,
        french: french,
      );
      if (!mounted) return;
      // Don't clobber a receipt that already rendered (e.g. this racing in after the user already
      // saw and acted on one composed a moment earlier) — first one to land wins, nothing flickers.
      if (_receiptText != null) return;
      setState(() {
        _receiptText = text;
        _receiptData = data;
      });
    } catch (_) {
      // No receipt UI shows up, but the collection is already safely recorded/queued regardless.
    }
  }

  /// Builds the signed payload behind "Show QR for Client" — used for both the online and
  /// offline success paths, since the client scanning their own copy is valuable either way, not
  /// just when there's no connectivity. Best-effort: a QR that fails to build never affects the
  /// collection itself, already recorded or safely queued by this point regardless.
  Future<void> _composeQrPayload({required String uniqueRef, required DateTime collectedAt, required List<DenominationLine> lines}) async {
    try {
      final context = await ReceiptContextCache().read();
      final payload = QrReceiptPayload(
        uniqueRef: uniqueRef,
        mfiName: context?.mfiName ?? 'MICROFI',
        branchName: context?.branchName ?? '—',
        agentEmployeeCode: widget.profile.employeeCode,
        agentFullName: widget.profile.fullName,
        clientMemberNo: _client!.mfiMemberNo,
        clientFullName: _client!.fullName,
        amountXaf: _total,
        collectedAtIso: collectedAt.toIso8601String(),
        denominationLines: lines,
        french: LocalePreference.instance.notifier.value.languageCode == 'fr',
      );
      if (!mounted) return;
      setState(() => _qrPayload = payload);
    } catch (_) {
      // No QR button shows up, but printing/downloading and the collection itself are unaffected.
    }
  }

  Widget _buildQueuedOffline() {
    final l10n = AppLocalizations.of(context)!;
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: MicrofiColors.tertiaryFixed,
            borderRadius: BorderRadius.circular(MicrofiRadius.lg),
            boxShadow: MicrofiShadows.soft,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.35), shape: BoxShape.circle),
                child: const Icon(Icons.schedule_rounded, color: MicrofiColors.onTertiaryFixedVariant, size: 26),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(l10n.csSavedOffline, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 18, color: MicrofiColors.onTertiaryFixedVariant)),
                    const SizedBox(height: 4),
                    Text(
                      l10n.csQueuedOfflineMessage(_fmt(_total), _client?.fullName ?? l10n.csGenericClient),
                      style: const TextStyle(color: MicrofiColors.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (_clockRolledBackWarning) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: MicrofiColors.errorContainer,
              borderRadius: BorderRadius.circular(MicrofiRadius.md),
            ),
            child: Row(
              children: [
                const Icon(Icons.access_time_rounded, color: MicrofiColors.onErrorContainer, size: 16),
                const SizedBox(width: 8),
                Expanded(child: Text(l10n.csClockRolledBackWarning, style: const TextStyle(fontSize: 12, color: MicrofiColors.onErrorContainer))),
              ],
            ),
          ),
        ],
        ..._receiptActionWidgets(),
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(l10n.commonDone),
          ),
        ),
      ],
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

class _StepperHeader extends StatelessWidget {
  final int step;

  const _StepperHeader({required this.step});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 6),
      child: Row(
        children: [
          _StepDot(label: l10n.csStepLabel(1), state: step > 0 ? _DotState.done : (step == 0 ? _DotState.current : _DotState.pending), number: 1),
          Expanded(child: Container(height: 2, color: step > 0 ? MicrofiColors.secondary : MicrofiColors.surfaceContainerHighest)),
          _StepDot(label: l10n.csStepLabel(2), state: step > 1 ? _DotState.done : (step == 1 ? _DotState.current : _DotState.pending), number: 2),
          Expanded(child: Container(height: 2, color: step > 1 ? MicrofiColors.secondary : MicrofiColors.surfaceContainerHighest)),
          _StepDot(label: l10n.csStepLabel(3), state: step == 2 ? _DotState.current : _DotState.pending, number: 3),
        ],
      ),
    );
  }
}

enum _DotState { done, current, pending }

class _StepDot extends StatelessWidget {
  final String label;
  final _DotState state;
  final int number;

  const _StepDot({required this.label, required this.state, required this.number});

  @override
  Widget build(BuildContext context) {
    final Color bg = switch (state) {
      _DotState.done => MicrofiColors.secondary,
      _DotState.current => MicrofiColors.primary,
      _DotState.pending => MicrofiColors.surfaceContainerLowest,
    };
    final Color fg = switch (state) {
      _DotState.done => MicrofiColors.onSecondary,
      _DotState.current => MicrofiColors.onPrimary,
      _DotState.pending => MicrofiColors.outline,
    };
    return Column(
      children: [
        Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(
            color: bg,
            shape: BoxShape.circle,
            border: state == _DotState.pending ? Border.all(color: MicrofiColors.outlineVariant, width: 2) : null,
            boxShadow: state == _DotState.pending ? null : [BoxShadow(color: bg.withValues(alpha: 0.35), blurRadius: 8, offset: const Offset(0, 3))],
          ),
          child: Center(
            child: state == _DotState.done
                ? const Icon(Icons.check_rounded, color: Colors.white, size: 15)
                : Text('$number', style: TextStyle(color: fg, fontWeight: FontWeight.bold, fontSize: 12)),
          ),
        ),
        const SizedBox(height: 3),
        Text(label, style: TextStyle(fontSize: 10, color: state == _DotState.pending ? MicrofiColors.outline : MicrofiColors.primary, fontWeight: state == _DotState.current ? FontWeight.w700 : FontWeight.w400)),
      ],
    );
  }
}

class _ClientContextCard extends StatelessWidget {
  final Client client;

  const _ClientContextCard({required this.client});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return _Card(
      child: Row(
        children: [
          Container(
            width: 36,
            height: 36,
            decoration: BoxDecoration(color: MicrofiColors.primaryFixedDim.withValues(alpha: 0.2), shape: BoxShape.circle),
            child: const Icon(Icons.account_circle, color: MicrofiColors.primary, size: 20),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.csTargetClientLabel, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.5)),
                Text(client.fullName, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: MicrofiColors.primary)),
                Text(client.mfiMemberNo, style: const TextStyle(color: MicrofiColors.outline, fontSize: 12)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CeilingPreviewBar extends StatelessWidget {
  final int projected;
  final int ceiling;

  const _CeilingPreviewBar({required this.projected, required this.ceiling});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final utilization = ceiling > 0 ? (projected / ceiling).clamp(0, 1).toDouble() : 0.0;
    final nearLimit = utilization >= 0.9;
    return _Card(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.speed, color: nearLimit ? MicrofiColors.error : MicrofiColors.secondary, size: 18),
              const SizedBox(width: 8),
              Text(l10n.csDailyCeilingImpact, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(MicrofiRadius.full),
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: utilization),
              duration: const Duration(milliseconds: 200),
              builder: (context, value, _) => LinearProgressIndicator(
                value: value,
                minHeight: 8,
                backgroundColor: MicrofiColors.surfaceContainerLow,
                valueColor: AlwaysStoppedAnimation(nearLimit ? MicrofiColors.error : MicrofiColors.secondary),
              ),
            ),
          ),
          const SizedBox(height: 6),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(l10n.csCeilingProgressLine(projected, ceiling), style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
              Text('${(utilization * 100).round()}%', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: nearLimit ? MicrofiColors.error : MicrofiColors.secondary)),
            ],
          ),
        ],
      ),
    );
  }
}

class _DenominationRow extends StatelessWidget {
  final int faceValue;
  final int count;
  final ValueChanged<int> onChanged;

  const _DenominationRow({required this.faceValue, required this.count, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 22,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: MicrofiColors.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: MicrofiColors.outlineVariant),
            ),
            child: const Text('BEAC', style: TextStyle(fontSize: 9, fontWeight: FontWeight.w700, color: MicrofiColors.onSurfaceVariant)),
          ),
          const SizedBox(width: 10),
          SizedBox(width: 64, child: Text('$faceValue', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16))),
          const Text('×', style: TextStyle(color: MicrofiColors.outline)),
          const Spacer(),
          SizedBox(
            width: 72,
            child: TextFormField(
              key: ValueKey('denom-$faceValue'),
              initialValue: count == 0 ? '' : count.toString(),
              keyboardType: TextInputType.number,
              textAlign: TextAlign.center,
              decoration: const InputDecoration(hintText: '0', contentPadding: EdgeInsets.symmetric(vertical: 10)),
              onChanged: (v) => onChanged(int.tryParse(v) ?? 0),
            ),
          ),
        ],
      ),
    );
  }
}

class _Card extends StatelessWidget {
  final Widget child;

  const _Card({required this.child});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.lg),
        boxShadow: MicrofiShadows.soft,
      ),
      child: child,
    );
  }
}
