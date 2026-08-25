import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_models.dart';
import 'client_repository.dart';
import '../../l10n/app_localizations.dart';

/// UC-21 — My Account. Balance is deliberately not shown — it isn't backed by a real CBS yet.
/// "Request Withdrawal" (Graphical Design/client/client_request_withdrawal) has no backend behind
/// it yet either — no withdrawal domain, no QR-verification endpoint exists in microfi-core — so
/// it's shown disabled and clearly labeled rather than wired to nothing.
class ClientWalletScreen extends StatefulWidget {
  final String token;

  const ClientWalletScreen({super.key, required this.token});

  @override
  State<ClientWalletScreen> createState() => _ClientWalletScreenState();
}

class _ClientWalletScreenState extends State<ClientWalletScreen> {
  late final ClientSelfRepository _repository = ClientSelfRepository(widget.token);

  ClientSelfProfile? _profile;
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
      final profile = await _repository.fetchProfile();
      if (!mounted) return;
      setState(() => _profile = profile);
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
    if (_error != null || _profile == null) {
      return ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const SizedBox(height: 50),
          const Icon(Icons.error_outline, color: MicrofiColors.error, size: 34),
          const SizedBox(height: 10),
          Text(_error ?? l10n.wsWalletUnavailable, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: MicrofiColors.error)),
          const SizedBox(height: 14),
          Center(child: FilledButton(onPressed: _load, child: Text(l10n.commonRetry))),
        ],
      );
    }

    final profile = _profile!;

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(MicrofiSpacing.page),
        children: [
          Text(l10n.cwMyAccountTitle, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: MicrofiColors.primary)),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Container(
            padding: const EdgeInsets.all(MicrofiSpacing.card),
            decoration: BoxDecoration(
              color: MicrofiColors.surfaceContainerLowest,
              borderRadius: BorderRadius.circular(MicrofiRadius.md),
              border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.cwBookletTokenTitle, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14, color: MicrofiColors.primary)),
                const SizedBox(height: 8),
                _InfoRow(label: l10n.cwStatusLabel, value: profile.tokenStatus),
                if (profile.tokenExpiresAt != null) _InfoRow(label: l10n.cwExpiresLabel, value: _fmtDate(profile.tokenExpiresAt!)),
              ],
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: null,
              icon: const Icon(Icons.qr_code_2, size: 18),
              label: Text(l10n.cwRequestWithdrawalComingSoon),
              style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(44)),
            ),
          ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 2),
            child: Text(
              l10n.cwWithdrawalNotAvailableNote,
              style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant),
            ),
          ),
        ],
      ),
    );
  }

  String _fmtDate(DateTime d) {
    final local = d.toLocal();
    return '${local.day}/${local.month}/${local.year}';
  }
}

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;

  const _InfoRow({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
          Text(value, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: MicrofiColors.primary)),
        ],
      ),
    );
  }
}
