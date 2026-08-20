import 'dart:async';

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:uuid/uuid.dart';
import '../../core/api_client.dart';
import '../../core/connectivity_service.dart';
import '../../core/design_tokens.dart';
import '../../core/location.dart';
import '../../core/printer_service.dart';
import '../../core/status_components.dart';
import '../home/agent_profile.dart';
import '../home/contact_branch.dart';
import '../home/home_repository.dart';
import 'client.dart';
import 'client_repository.dart';
import 'collection_repository.dart';
import 'notification_repository.dart';
import 'offline_queue_repository.dart';

const List<int> _denominations = [10000, 5000, 2000, 1000, 500, 200, 100, 50, 25];

/// Graphical Design/agent/collection_denominations — the 3-step Collection Wizard: find client,
/// count denominations (with a live daily-ceiling preview), confirm and submit.
class CollectionStepperScreen extends StatefulWidget {
  final String token;
  final String agentId;

  const CollectionStepperScreen({super.key, required this.token, required this.agentId});

  @override
  State<CollectionStepperScreen> createState() => _CollectionStepperScreenState();
}

class _CollectionStepperScreenState extends State<CollectionStepperScreen> {
  late final ClientRepository _clientRepository = ClientRepository(widget.token);
  late final CollectionRepository _collectionRepository = CollectionRepository(widget.token);
  late final HomeRepository _homeRepository = HomeRepository(widget.token);
  late final NotificationRepository _notificationRepository = NotificationRepository(widget.token);
  final PrinterService _printerService = PrinterService();

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

  // Step 3 — confirm/submit
  final _pinController = TextEditingController();
  bool _submitting = false;
  String? _submitError;
  CollectionResult? _result;
  bool _queuedOffline = false;
  String? _receiptText;
  bool _printing = false;
  bool _printed = false;
  String? _printError;

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
      setState(() => _searchError = 'Unable to reach the server.');
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
    } catch (_) {
      // Live ceiling preview is a courtesy, not a gate — the server still enforces BR-03 on submit.
    }
  }

  int get _total => _denominations.fold(0, (sum, d) => sum + d * (_counts[d] ?? 0));

  Future<void> _submit() async {
    if (_total <= 0 || _position == null || _client == null) return;
    setState(() {
      _submitting = true;
      _submitError = null;
    });

    final deviceTxId = const Uuid().v4();
    final lines = _denominations
        .where((d) => (_counts[d] ?? 0) > 0)
        .map((d) => DenominationLine(faceValueXaf: d, quantity: _counts[d]!))
        .toList();

    final online = await ConnectivityService.instance.isOnline();
    if (!online) {
      // No PIN collected here — it's never persisted to the local offline queue, only prompted
      // once at sync time (Home's "Sync Now"), so a stolen/rooted phone with a local queue file
      // never has the PIN sitting on disk.
      await OfflineQueueRepository(widget.agentId).add(PendingCollection(
        deviceTxId: deviceTxId,
        clientId: _client!.id,
        clientName: _client!.fullName,
        amountXaf: _total,
        lat: _position!.latitude,
        lon: _position!.longitude,
        accuracyM: _position!.accuracy,
        collectedAtIso: DateTime.now().toUtc().toIso8601String(),
        denominationLines: lines,
      ));
      if (!mounted) return;
      setState(() {
        _queuedOffline = true;
        _submitting = false;
      });
      return;
    }

    if (_pinController.text.isEmpty) {
      setState(() {
        _submitError = 'Enter your PIN to confirm this collection.';
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
        denominationLines: lines,
        pin: _pinController.text,
      );
      if (!mounted) return;
      setState(() => _result = result);
      _notifyAfterSuccess(result.id);
    } on ApiException catch (e) {
      setState(() => _submitError = e.message);
    } catch (_) {
      setState(() => _submitError = 'Unable to reach the server.');
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  /// UC-09: fires the Flash SMS attempt and fetches the receipt text as soon as the collection
  /// succeeds — best-effort, since an SMS-gateway failure (e.g. no carrier credentials configured
  /// yet) must never look like a problem with the collection itself, which already succeeded.
  Future<void> _notifyAfterSuccess(String collectionId) async {
    try {
      final result = await _notificationRepository.notifyCollection(collectionId, printedReceipt: false);
      if (!mounted) return;
      setState(() => _receiptText = result.receiptText);
    } catch (_) {
      // Silent — SMS delivery status isn't something the agent needs to act on here; the
      // Back-Office notification audit log is the place to investigate a failed send.
    }
  }

  Future<void> _printReceipt() async {
    final receiptText = _receiptText;
    if (receiptText == null) return;
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
        throw PrinterUnavailable('Printer did not confirm the print.');
      }
      if (_result != null) {
        await _notificationRepository.notifyCollection(_result!.id, printedReceipt: true);
      }
      if (!mounted) return;
      setState(() => _printed = true);
    } on PrinterUnavailable catch (e) {
      if (!mounted) return;
      setState(() => _printError = e.message);
    } catch (_) {
      if (!mounted) return;
      setState(() => _printError = 'Could not print the receipt.');
    } finally {
      if (mounted) setState(() => _printing = false);
    }
  }

  Future<PairedPrinter?> _pickPrinter() async {
    if (!await _printerService.isReady()) {
      setState(() => _printError = 'Turn on Bluetooth and grant permission, then try again.');
      return null;
    }
    final printers = await _printerService.pairedPrinters();
    if (!mounted) return null;
    if (printers.isEmpty) {
      setState(() => _printError = 'No paired thermal printer found — pair one in your phone\'s Bluetooth settings first.');
      return null;
    }
    return showDialog<PairedPrinter>(
      context: context,
      builder: (context) => SimpleDialog(
        title: const Text('Select Thermal Printer'),
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
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: Scaffold(
        backgroundColor: Colors.transparent,
        appBar: AppBar(
          leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: _back),
          title: const Text('Collect Cash'),
        ),
        body: SafeArea(
          child: Column(
            children: [
              if (_result == null && !_queuedOffline) _StepperHeader(step: _step),
              Expanded(child: _buildStep()),
            ],
          ),
        ),
      ),
    );
  }

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
              hintText: 'Name, phone, or member number',
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
              _searchController.text.trim().isEmpty ? 'No clients registered yet.' : 'No clients found for that search.',
              style: const TextStyle(color: MicrofiColors.onSurfaceVariant),
            ),
          ),
        Expanded(
          child: ListView.builder(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: _searchResults.length,
            itemBuilder: (context, index) {
              final c = _searchResults[index];
              return Card(
                margin: const EdgeInsets.only(bottom: 10),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(MicrofiRadius.md),
                  side: const BorderSide(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
                ),
                elevation: 0,
                color: MicrofiColors.surfaceContainerLowest,
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  title: Text(c.fullName, style: const TextStyle(fontWeight: FontWeight.w700)),
                  subtitle: Text('${c.mfiMemberNo} • ${c.phone}'),
                  trailing: const Icon(Icons.chevron_right),
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
    final client = _client!;
    final canContinue = _total > 0 && _position != null;
    final projected = (_escrow?.cumulativeTodayXaf ?? 0) + _total;
    final ceiling = _escrow?.effectiveCeilingXaf ?? 0;
    final wouldExceed = _escrow != null && ceiling > 0 && projected > ceiling;

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
                      ? 'Capturing GPS location…'
                      : _position != null
                          ? 'Location captured (±${_position!.accuracy.round()}m)'
                          : (_locationError ?? 'Location unavailable'),
                  style: TextStyle(color: _locationError != null ? MicrofiColors.error : MicrofiColors.onSurfaceVariant),
                ),
              ),
              if (!_locating && _position == null) TextButton(onPressed: _captureLocation, child: const Text('Retry')),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Container(
          decoration: BoxDecoration(
            color: MicrofiColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(MicrofiRadius.md),
            border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
          ),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: MicrofiColors.outlineVariant))),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: const [
                    Text('Denomination Breakdown', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                    Icon(Icons.payments, color: MicrofiColors.outline),
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
                padding: const EdgeInsets.all(14),
                decoration: const BoxDecoration(
                  color: MicrofiColors.surfaceContainer,
                  borderRadius: BorderRadius.only(bottomLeft: Radius.circular(MicrofiRadius.md - 2), bottomRight: Radius.circular(MicrofiRadius.md - 2)),
                ),
                child: Column(
                  children: [
                    const Text('CALCULATED TOTAL', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.5)),
                    const SizedBox(height: 6),
                    Text('${_fmt(_total)} XAF', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                  ],
                ),
              ),
            ],
          ),
        ),
        if (_escrow != null) ...[
          const SizedBox(height: 12),
          if (wouldExceed)
            EscrowCeilingReachedCard(
              message: 'This collection would push you to ${_fmt(projected)} / ${_fmt(ceiling)} XAF — ${_fmt(projected - ceiling)} XAF over your daily ceiling.',
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
            label: const Text('Verify & Continue'),
            style: FilledButton.styleFrom(shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.full))),
          ),
        ),
      ],
    );
  }

  Widget _buildConfirmStep() {
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
              const Text('Confirm Collection', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
              const SizedBox(height: 12),
              ..._denominations.where((d) => (_counts[d] ?? 0) > 0).map((d) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('$d XAF × ${_counts[d]}', style: const TextStyle(color: MicrofiColors.onSurfaceVariant)),
                        Text('${_fmt(d * (_counts[d] ?? 0))} XAF', style: const TextStyle(fontWeight: FontWeight.w600)),
                      ],
                    ),
                  )),
              const Divider(color: MicrofiColors.outlineVariant, height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('Total', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                  Text('${_fmt(_total)} XAF', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                'Location: ${_position!.latitude.toStringAsFixed(5)}, ${_position!.longitude.toStringAsFixed(5)} (±${_position!.accuracy.round()}m)',
                style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        _Card(
          child: TextField(
            controller: _pinController,
            decoration: const InputDecoration(
              labelText: 'Enter Your PIN to Confirm',
              prefixIcon: Icon(Icons.lock_outline),
              border: InputBorder.none,
            ),
            obscureText: true,
            keyboardType: TextInputType.number,
          ),
        ),
        if (_submitError != null) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(color: MicrofiColors.errorContainer, borderRadius: BorderRadius.circular(MicrofiRadius.sm)),
            child: Text(_submitError!, style: const TextStyle(color: MicrofiColors.onErrorContainer)),
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
                : const Text('Record Collection'),
          ),
        ),
      ],
    );
  }

  Widget _buildSuccess() {
    final result = _result!;
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        const SizedBox(height: 20),
        SuccessCard(
          title: result.duplicate ? 'Already Recorded' : 'Collection Validated',
          subtitle: result.locationName != null
              ? 'Transaction ID: ${result.id.substring(0, 8).toUpperCase()} • ${result.locationName}'
              : 'Transaction ID: ${result.id.substring(0, 8).toUpperCase()}',
          amountLabel: 'Amount Collected',
          amountValue: '${_fmt(result.amountXaf)} XAF',
        ),
        if (_receiptText != null) ...[
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            height: 44,
            child: OutlinedButton.icon(
              onPressed: _printing || _printed ? null : _printReceipt,
              icon: Icon(_printed ? Icons.check_circle : Icons.print, size: 18),
              label: Text(_printed ? 'Receipt Printed' : (_printing ? 'Printing…' : 'Print Receipt')),
            ),
          ),
          if (_printError != null)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(_printError!, style: const TextStyle(color: MicrofiColors.error, fontSize: 12)),
            ),
        ],
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Done'),
          ),
        ),
      ],
    );
  }

  Widget _buildQueuedOffline() {
    return ListView(
      padding: const EdgeInsets.all(12),
      children: [
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: MicrofiColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(MicrofiRadius.md),
            border: Border.all(color: MicrofiColors.tertiaryFixedDim, width: MicrofiBorders.width),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 48,
                height: 44,
                decoration: const BoxDecoration(color: MicrofiColors.tertiaryFixedDim, shape: BoxShape.circle),
                child: const Icon(Icons.schedule, color: MicrofiColors.onTertiaryFixedVariant, size: 26),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Saved Offline', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 18, color: MicrofiColors.onTertiaryFixedVariant)),
                    const SizedBox(height: 4),
                    Text(
                      '${_fmt(_total)} XAF for ${_client?.fullName ?? 'client'} is queued. It will sync automatically once you\'re back online.',
                      style: const TextStyle(color: MicrofiColors.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          height: 44,
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Done'),
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
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 6),
      child: Row(
        children: [
          _StepDot(label: 'Step 1', state: step > 0 ? _DotState.done : (step == 0 ? _DotState.current : _DotState.pending), number: 1),
          Expanded(child: Container(height: 2, color: step > 0 ? MicrofiColors.secondary : MicrofiColors.surfaceContainerHighest)),
          _StepDot(label: 'Step 2', state: step > 1 ? _DotState.done : (step == 1 ? _DotState.current : _DotState.pending), number: 2),
          Expanded(child: Container(height: 2, color: step > 1 ? MicrofiColors.secondary : MicrofiColors.surfaceContainerHighest)),
          _StepDot(label: 'Step 3', state: step == 2 ? _DotState.current : _DotState.pending, number: 3),
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
          width: 26,
          height: 26,
          decoration: BoxDecoration(
            color: bg,
            shape: BoxShape.circle,
            border: state == _DotState.pending ? Border.all(color: MicrofiColors.outlineVariant, width: 2) : null,
          ),
          child: Center(
            child: state == _DotState.done
                ? const Icon(Icons.check, color: Colors.white, size: 14)
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
                const Text('TARGET CLIENT', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600, color: MicrofiColors.onSurfaceVariant, letterSpacing: 0.5)),
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
              const Text('Daily Ceiling Impact', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
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
              Text('$projected / $ceiling XAF', style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
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
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
      ),
      child: child,
    );
  }
}
