import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:url_launcher/url_launcher.dart';
import 'branch_repository.dart';
import '../../l10n/app_localizations.dart';

const _phoneChannel = MethodChannel('com.microfi.microfi_mobile/phone');

/// Places the call directly (ACTION_CALL) for the agent's own branch (GET /agents/me/branch),
/// with a graceful message if the branch has no number on file. Shared by every "Contact Branch"
/// entry point — Home's always-available action and the escrow-ceiling-reached card — so there's
/// exactly one place this logic lives.
Future<void> contactBranch(BuildContext context, String token) async {
  try {
    final branch = await BranchRepository(token).fetchMyBranch();
    if (!context.mounted) return;
    if (branch.phone == null || branch.phone!.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.cbNoPhoneOnFile(branch.name))),
      );
      return;
    }
    if (await _tryDirectCall(branch.phone!)) return;

    // Fell through: not Android, permission refused, or the native call attempt itself failed.
    // tel: still gets the call placed (via the device's own dialer/confirmation UI) rather than
    // leaving the agent with nothing, just without skipping that extra tap/confirmation.
    if (!context.mounted) return;
    final uri = Uri(scheme: 'tel', path: branch.phone);
    if (!await launchUrl(uri)) throw Exception('launch failed');
  } catch (_) {
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(AppLocalizations.of(context)!.cbUnableToOpenDialer)),
    );
  }
}

/// ACTION_CALL requires the CALL_PHONE runtime permission (requested here) and a small native
/// method channel (see MainActivity.kt) — no plugin in this project's dependency tree wraps it.
/// iOS has no public API to place a call without the system's own confirmation UI, so this is
/// Android-only; other platforms fall back to the tel: launch above.
Future<bool> _tryDirectCall(String phoneNumber) async {
  if (!Platform.isAndroid) return false;
  final status = await Permission.phone.request();
  if (!status.isGranted) return false;
  try {
    final placed = await _phoneChannel.invokeMethod<bool>('placeCall', {'number': phoneNumber});
    return placed == true;
  } catch (_) {
    return false;
  }
}
