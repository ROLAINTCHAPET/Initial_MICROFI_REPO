import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/dialogs.dart';
import 'client_repository.dart';
import '../../l10n/app_localizations.dart';

/// A client's report that their agent may have misbehaved (UC-14's counterpart for the client
/// side — see AgentMisconductReportController). The client only ever sees their own collection
/// records, never agent names, so the picker identifies an agent by the date of a collection they
/// made rather than a name lookup that doesn't exist on this side of the API.
class ClientReportAgentScreen extends StatefulWidget {
  final String token;

  const ClientReportAgentScreen({super.key, required this.token});

  @override
  State<ClientReportAgentScreen> createState() => _ClientReportAgentScreenState();
}

class _AgentOption {
  final String agentId;
  final DateTime lastCollectedAt;

  _AgentOption({required this.agentId, required this.lastCollectedAt});
}

class _ClientReportAgentScreenState extends State<ClientReportAgentScreen> {
  final _formKey = GlobalKey<FormState>();
  final _reasonController = TextEditingController();
  late final ClientSelfRepository _repository = ClientSelfRepository(widget.token);

  bool _loading = true;
  bool _submitting = false;
  List<_AgentOption> _agents = [];
  String? _selectedAgentId;

  @override
  void initState() {
    super.initState();
    _loadAgents();
  }

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  Future<void> _loadAgents() async {
    try {
      final collections = await _repository.fetchRecentCollections();
      if (!mounted) return;
      // Most-recent-first, deduplicated by agentId — a client typically only ever needs to report
      // whichever agent they most recently dealt with.
      final seen = <String>{};
      final options = <_AgentOption>[];
      for (final c in collections) {
        if (seen.add(c.agentId)) {
          options.add(_AgentOption(agentId: c.agentId, lastCollectedAt: c.collectedAt));
        }
      }
      setState(() {
        _agents = options;
        _selectedAgentId = options.isNotEmpty ? options.first.agentId : null;
      });
    } catch (_) {
      // Best-effort — an empty list just shows craNoAgentsMessage below.
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _submit() async {
    final l10n = AppLocalizations.of(context)!;
    if (!_formKey.currentState!.validate() || _selectedAgentId == null) return;
    setState(() => _submitting = true);
    try {
      await _repository.reportMisconduct(agentId: _selectedAgentId!, reason: _reasonController.text.trim());
      if (!mounted) return;
      await showSuccessDialog(context, l10n.craSuccessMessage, title: l10n.craSuccessTitle);
      if (!mounted) return;
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      await showErrorDialog(context, e, title: l10n.craFailedTitle);
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  String _fmtDate(DateTime d) {
    final local = d.toLocal();
    return '${local.day}/${local.month}/${local.year}';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.craTitle)),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(16),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 400),
                    child: Container(
                      padding: const EdgeInsets.all(20),
                      decoration: BoxDecoration(
                        color: MicrofiColors.surfaceContainerLowest,
                        borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                        boxShadow: MicrofiShadows.soft,
                      ),
                      child: _agents.isEmpty ? _buildEmptyState(l10n) : _buildForm(l10n),
                    ),
                  ),
                ),
              ),
      ),
    );
  }

  Widget _buildEmptyState(AppLocalizations l10n) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.info_outline_rounded, color: MicrofiColors.onSurfaceVariant, size: 32),
        const SizedBox(height: 12),
        Text(l10n.craNoAgentsMessage, textAlign: TextAlign.center, style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant)),
      ],
    );
  }

  Widget _buildForm(AppLocalizations l10n) {
    return Form(
      key: _formKey,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: MicrofiColors.primary.withValues(alpha: 0.06),
              borderRadius: BorderRadius.circular(MicrofiRadius.md),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.shield_outlined, color: MicrofiColors.primary, size: 16),
                const SizedBox(width: 8),
                Expanded(child: Text(l10n.craIntro, style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant))),
              ],
            ),
          ),
          const SizedBox(height: MicrofiSpacing.gapLg),
          Text(l10n.craAgentLabel, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
          const SizedBox(height: 8),
          ..._agents.map((a) => RadioListTile<String>(
                value: a.agentId,
                groupValue: _selectedAgentId,
                onChanged: (v) => setState(() => _selectedAgentId = v),
                dense: true,
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.craAgentOption(_fmtDate(a.lastCollectedAt)), style: const TextStyle(fontSize: 13)),
              )),
          const SizedBox(height: MicrofiSpacing.gap),
          Text(l10n.craReasonLabel, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
          const SizedBox(height: 8),
          TextFormField(
            controller: _reasonController,
            maxLines: 4,
            decoration: InputDecoration(hintText: l10n.craReasonHint),
            validator: (v) => (v == null || v.trim().isEmpty) ? l10n.craReasonRequired : null,
          ),
          const SizedBox(height: 18),
          FilledButton(
            onPressed: _submitting ? null : _submit,
            child: _submitting
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : Text(l10n.craSubmitButton),
          ),
        ],
      ),
    );
  }
}
