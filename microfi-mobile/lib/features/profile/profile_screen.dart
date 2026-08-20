import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../auth/pin_setup_screen.dart';
import '../home/agent_profile.dart';
import '../home/branch_repository.dart';

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
    final profile = _profile;
    return Scaffold(
      backgroundColor: Colors.transparent,
      appBar: AppBar(title: const Text('My Profile')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(MicrofiSpacing.page),
          children: [
            Center(
              child: CircleAvatar(
                radius: 32,
                backgroundColor: MicrofiColors.surfaceContainerHigh,
                child: Text(
                  profile.fullName.isNotEmpty ? profile.fullName[0].toUpperCase() : '?',
                  style: const TextStyle(color: MicrofiColors.primary, fontSize: 24, fontWeight: FontWeight.bold),
                ),
              ),
            ),
            const SizedBox(height: 10),
            Center(
              child: Text(profile.fullName, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
            ),
            const SizedBox(height: 18),
            Container(
              padding: const EdgeInsets.all(MicrofiSpacing.card),
              decoration: BoxDecoration(
                color: MicrofiColors.surfaceContainerLowest,
                borderRadius: BorderRadius.circular(MicrofiRadius.md),
                border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
              ),
              child: Column(
                children: [
                  _Row(label: 'Username', value: profile.username),
                  _Row(label: 'Employee Code', value: profile.employeeCode),
                  if (profile.email != null) _Row(label: 'Email', value: profile.email!),
                  _Row(label: 'Phone', value: profile.phone),
                  _Row(label: 'Device Binding', value: profile.imei != null ? 'Bound' : 'Not bound (own device)'),
                  _Row(label: 'Branch', value: _branchName ?? '…'),
                  _Row(label: 'Status', value: profile.status),
                ],
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              height: 44,
              child: OutlinedButton.icon(
                onPressed: _changePin,
                icon: const Icon(Icons.lock_reset_outlined),
                label: const Text('Change Transaction PIN'),
              ),
            ),
            const SizedBox(height: 16),
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 4),
              child: Text(
                'To change your username, password, or other details, contact your branch back-office.',
                style: TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Row extends StatelessWidget {
  final String label;
  final String value;

  const _Row({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 120, child: Text(label, style: const TextStyle(color: MicrofiColors.onSurfaceVariant))),
          Expanded(child: Text(value, style: const TextStyle(fontWeight: FontWeight.w600))),
        ],
      ),
    );
  }
}
