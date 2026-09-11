import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/language_picker.dart';
import '../auth/pin_setup_screen.dart';
import '../home/agent_profile.dart';
import '../home/branch_repository.dart';
import '../../l10n/app_localizations.dart';

/// Mostly read-only — there's no self-service field on the backend an agent can safely edit
/// themselves beyond their transaction PIN (employee code/IMEI are security-bound identity
/// fields; branch/status are admin-controlled). Everything else goes through the back-office.
class ProfileScreen extends StatefulWidget {
  final String token;
  final AgentProfile profile;

  const ProfileScreen({super.key, required this.token, required this.profile});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  late AgentProfile _profile = widget.profile;
  String? _branchName;

  @override
  void initState() {
    super.initState();
    BranchRepository(widget.token).fetchMyBranch().then((branch) {
      if (mounted) setState(() => _branchName = branch.name);
    }).catchError((_) {
      // Branch name is a display nicety here — fall back to showing nothing rather than an error.
    });
  }

  Future<void> _changePin() async {
    final updated = await Navigator.of(context).push<AgentProfile>(
      MaterialPageRoute(
        builder: (_) => PinSetupScreen(token: widget.token, profile: _profile, mandatory: false),
      ),
    );
    if (updated != null && mounted) setState(() => _profile = updated);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final profile = _profile;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: Text(l10n.prMyProfileTitle), actions: const [Padding(padding: EdgeInsets.only(right: 12), child: LanguagePickerButton())]),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(MicrofiSpacing.page),
          children: [
            Center(
              child: Container(
                width: 84,
                height: 84,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: const LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [MicrofiColors.primary, MicrofiColors.primaryContainer],
                  ),
                  boxShadow: [BoxShadow(color: MicrofiColors.primary.withValues(alpha: 0.3), blurRadius: 18, offset: const Offset(0, 6))],
                ),
                child: Text(
                  profile.fullName.isNotEmpty ? profile.fullName[0].toUpperCase() : '?',
                  style: const TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w800),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Center(
              child: Text(profile.fullName, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: MicrofiColors.primary)),
            ),
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: MicrofiColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(MicrofiRadius.lg),
                boxShadow: MicrofiShadows.soft,
              ),
              child: Column(
                children: [
                  _Row(icon: Icons.person_outline_rounded, label: l10n.lgUsernameLabel, value: profile.username),
                  _Row(icon: Icons.badge_outlined, label: l10n.prEmployeeCodeLabel, value: profile.employeeCode),
                  if (profile.email != null) _Row(icon: Icons.email_outlined, label: l10n.prEmailLabel, value: profile.email!),
                  _Row(icon: Icons.call_outlined, label: l10n.prPhoneLabel, value: profile.phone),
                  _Row(icon: Icons.smartphone_outlined, label: l10n.prDeviceBindingLabel, value: profile.imei != null ? l10n.prBound : l10n.prNotBoundOwnDevice),
                  _Row(icon: Icons.location_on_outlined, label: l10n.rpBranchLabel, value: _branchName ?? '…'),
                  _Row(icon: Icons.verified_outlined, label: l10n.cwStatusLabel, value: profile.status, last: true),
                ],
              ),
            ),
            const SizedBox(height: 14),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: OutlinedButton.icon(
                onPressed: _changePin,
                icon: const Icon(Icons.lock_reset_rounded),
                label: Text(l10n.prChangeTransactionPin),
              ),
            ),
            const SizedBox(height: 16),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                l10n.prContactBackOfficeNote,
                style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Row extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final bool last;

  const _Row({required this.icon, required this.label, required this.value, this.last = false});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 12),
      decoration: last
          ? null
          : const BoxDecoration(border: Border(bottom: BorderSide(color: MicrofiColors.outlineVariant, width: 0.75))),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Icon(icon, size: 18, color: MicrofiColors.outline),
          const SizedBox(width: 12),
          Expanded(child: Text(label, style: const TextStyle(color: MicrofiColors.onSurfaceVariant, fontSize: 13))),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: MicrofiColors.primary)),
        ],
      ),
    );
  }
}
