import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'api_client.dart';
import 'design_tokens.dart';
import '../l10n/app_localizations.dart';

/// Turns any caught error into a short, human sentence — never the raw exception text (a
/// `ClientException: Failed to fetch, uri=...` or similar is a dev error, not something a field
/// agent should ever see). Every catch block in the app should route through this before display.
String friendlyErrorMessage(BuildContext context, Object error) {
  final l10n = AppLocalizations.of(context)!;
  if (error is ApiException) {
    // Spring Security's default 401 body carries no custom message, just "Unauthorized" — too
    // terse to be useful; every other status already has a real, specific message from the backend.
    if (error.statusCode == 401 && error.message.toLowerCase() == 'unauthorized') {
      return l10n.errorIncorrectCredentials;
    }
    return error.message;
  }
  if (error is TimeoutException) return l10n.errorRequestTimeout;
  if (error is SocketException || error is HttpException) return l10n.errorUnableToReachServer;
  final text = error.toString();
  if (text.contains('Failed to fetch') || text.contains('ClientException') || text.contains('SocketException')) {
    return l10n.errorUnableToReachServer;
  }
  return l10n.errorSomethingWentWrong;
}

/// The one error-dialog implementation for the whole app (Escrow Ceiling Reached's red-alert
/// style, generalized) — every failure surfaces this way instead of an inline banner or raw text.
Future<void> showErrorDialog(BuildContext context, Object error, {String? title}) {
  final l10n = AppLocalizations.of(context)!;
  return showDialog<void>(
    context: context,
    builder: (_) => AlertDialog(
      icon: Container(
        width: 48,
        height: 48,
        decoration: const BoxDecoration(color: MicrofiColors.error, shape: BoxShape.circle),
        child: const Icon(Icons.error_outline, color: Colors.white, size: 26),
      ),
      title: Text(title ?? l10n.errorDialogTitle, textAlign: TextAlign.center),
      content: Text(friendlyErrorMessage(context, error), textAlign: TextAlign.center),
      actions: [
        Center(
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.commonOk),
          ),
        ),
      ],
    ),
  );
}

/// Prompts once for the transaction PIN before syncing queued offline collections — the PIN is
/// never persisted with the local queue (see PendingCollection), so it has to be re-entered at
/// sync time and applied to every item in that batch. Returns null if the agent cancels.
Future<String?> promptForPin(BuildContext context, {required String message}) {
  final l10n = AppLocalizations.of(context)!;
  final controller = TextEditingController();
  return showDialog<String>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      icon: Container(
        width: 48,
        height: 48,
        decoration: const BoxDecoration(color: MicrofiColors.primary, shape: BoxShape.circle),
        child: const Icon(Icons.lock_outline, color: Colors.white, size: 26),
      ),
      title: Text(l10n.dialogEnterPinTitle, textAlign: TextAlign.center),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          TextField(
            controller: controller,
            autofocus: true,
            obscureText: true,
            keyboardType: TextInputType.number,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(prefixIcon: Icon(Icons.lock_outline)),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(dialogContext).pop(), child: Text(l10n.commonCancel)),
        FilledButton(
          onPressed: () => Navigator.of(dialogContext).pop(controller.text),
          child: Text(l10n.commonConfirm),
        ),
      ],
    ),
  );
}

/// The one success-dialog implementation for simple confirmations (distinct from the fuller
/// SuccessCard used inline on the collection-confirmation screen).
Future<void> showSuccessDialog(BuildContext context, String message, {String? title}) {
  final l10n = AppLocalizations.of(context)!;
  return showDialog<void>(
    context: context,
    builder: (_) => AlertDialog(
      icon: Container(
        width: 48,
        height: 48,
        decoration: const BoxDecoration(color: MicrofiColors.secondary, shape: BoxShape.circle),
        child: const Icon(Icons.check, color: Colors.white, size: 26),
      ),
      title: Text(title ?? l10n.commonDone, textAlign: TextAlign.center),
      content: Text(message, textAlign: TextAlign.center),
      actions: [
        Center(
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text(l10n.commonOk),
          ),
        ),
      ],
    ),
  );
}
