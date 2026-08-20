import 'dart:async';

import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'sponsor_activation_repository.dart';

/// UC-19 step 2 — the agent's side of the two-party activation gate. Lands directly on the list
/// of clients who've already self-activated (set their own login) but have no live booklet token
/// yet, with a search bar (name/phone/member number) to narrow it down — no need to ask the client
/// for a login string, since there's a real candidate list to tap instead.
class SponsorActivationScreen extends StatefulWidget {
  final String token;

  const SponsorActivationScreen({super.key, required this.token});

  @override
  State<SponsorActivationScreen> createState() => _SponsorActivationScreenState();
}

class _SponsorActivationScreenState extends State<SponsorActivationScreen> {
  final _searchController = TextEditingController();
  Timer? _debounce;
  late final _repository = SponsorActivationRepository(widget.token);

  List<PendingClientActivation> _results = [];
  bool _loading = false;
  String? _error;
  String? _sponsoringId;

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
    super.dispose();
  }

  void _onQueryChanged() {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), _search);
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await _repository.listPending(_searchController.text);
      if (!mounted) return;
      setState(() => _results = results);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = friendlyErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _confirmSponsor(PendingClientActivation c) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Sponsor Activation?'),
        content: Text("Confirm you have received ${c.fullName}'s activation fee in cash."),
        actions: [
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.of(dialogContext).pop(true), child: const Text('Confirm')),
        ],
      ),
    );
    if (confirmed != true) return;
    if (!mounted) return;

    setState(() => _sponsoringId = c.id);
    try {
      final result = await _repository.sponsor(c.id);
      if (!mounted) return;
      final active = result.status == 'ACTIVE';
      await showSuccessDialog(
        context,
        active
            ? '${c.fullName} is now fully activated — both sponsorship and payment are confirmed.'
            : 'Sponsorship recorded for ${c.fullName}. Still waiting on their own payment confirmation.',
        title: active ? 'Client Activated' : 'Sponsorship Recorded',
      );
      if (!mounted) return;
      await _search();
    } catch (e) {
      if (!mounted) return;
      await showErrorDialog(context, e, title: 'Sponsorship Failed');
    } finally {
      if (mounted) setState(() => _sponsoringId = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: const Text('Sponsor Activation')),
      body: SafeArea(
        child: Column(
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
            if (_loading) const Padding(padding: EdgeInsets.all(16), child: CircularProgressIndicator()),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Text(_error!, style: const TextStyle(color: MicrofiColors.error), textAlign: TextAlign.center),
              ),
            if (!_loading && _results.isEmpty && _error == null)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  _searchController.text.trim().isEmpty
                      ? 'No clients are currently awaiting activation.'
                      : 'No clients found for that search.',
                  style: const TextStyle(color: MicrofiColors.onSurfaceVariant),
                  textAlign: TextAlign.center,
                ),
              ),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                itemCount: _results.length,
                itemBuilder: (context, index) {
                  final c = _results[index];
                  final busy = _sponsoringId == c.id;
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
                      trailing: c.sponsored
                          ? const Chip(
                              label: Text('Awaiting payment', style: TextStyle(fontSize: 11)),
                              backgroundColor: MicrofiColors.surfaceContainerHigh,
                              visualDensity: VisualDensity.compact,
                            )
                          : busy
                              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                              : FilledButton(
                                  onPressed: () => _confirmSponsor(c),
                                  style: FilledButton.styleFrom(minimumSize: const Size(0, 36), padding: const EdgeInsets.symmetric(horizontal: 12)),
                                  child: const Text('Sponsor', style: TextStyle(fontSize: 12)),
                                ),
                      onTap: c.sponsored || busy ? null : () => _confirmSponsor(c),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
