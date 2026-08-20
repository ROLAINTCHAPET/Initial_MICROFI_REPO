import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';
import 'branch_repository.dart';

/// Opens the phone dialer for the agent's own branch (GET /agents/me/branch), with a graceful
/// message if the branch has no number on file. Shared by every "Contact Branch" entry point —
/// Home's always-available action and the escrow-ceiling-reached card — so there's exactly one
/// place this logic lives.
Future<void> contactBranch(BuildContext context, String token) async {
  try {
    final branch = await BranchRepository(token).fetchMyBranch();
    if (!context.mounted) return;
    if (branch.phone == null || branch.phone!.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('No phone number on file for ${branch.name}.')),
      );
      return;
    }
    final uri = Uri(scheme: 'tel', path: branch.phone);
    if (!await launchUrl(uri)) throw Exception('launch failed');
  } catch (_) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Unable to open the phone dialer.')),
    );
  }
}
