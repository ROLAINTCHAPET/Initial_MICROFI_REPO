import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'api_client.dart';
import 'design_tokens.dart';

/// Turns any caught error into a short, human sentence — never the raw exception text (a
/// `ClientException: Failed to fetch, uri=...` or similar is a dev error, not something a field
/// agent should ever see). Every catch block in the app should route through this before display.
String friendlyErrorMessage(Object error) {
  if (error is ApiException) {
    // Spring Security's default 401 body carries no custom message, just "Unauthorized" — too
    // terse to be useful; every other status already has a real, specific message from the backend.
    if (error.statusCode == 401 && error.message.toLowerCase() == 'unauthorized') {
      return 'Incorrect login credentials.';
    }
    return error.message;
  }
  if (error is TimeoutException) return 'The request took too long. Please try again.';
  if (error is SocketException || error is HttpException) return 'Unable to reach the server. Check your connection.';
  final text = error.toString();
  if (text.contains('Failed to fetch') || text.contains('ClientException') || text.contains('SocketException')) {
    return 'Unable to reach the server. Check your connection.';
  }
  return 'Something went wrong. Please try again.';
}

/// The one error-dialog implementation for the whole app (Escrow Ceiling Reached's red-alert
/// style, generalized) — every failure surfaces this way instead of an inline banner or raw text.
Future<void> showErrorDialog(BuildContext context, Object error, {String title = 'Something Went Wrong'}) {
  return showDialog<void>(
    context: context,
    builder: (_) => AlertDialog(
      icon: Container(
        width: 48,
        height: 48,
        decoration: const BoxDecoration(color: MicrofiColors.error, shape: BoxShape.circle),
        child: const Icon(Icons.error_outline, color: Colors.white, size: 26),
      ),
      title: Text(title, textAlign: TextAlign.center),
      content: Text(friendlyErrorMessage(error), textAlign: TextAlign.center),
      actions: [
        Center(
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
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
      title: const Text('Enter Your PIN', textAlign: TextAlign.center),
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
            decoration: const InputDecoration(prefixIcon: Icon(Icons.lock_outline)),
          ),
        ],
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(dialogContext).pop(), child: const Text('Cancel')),
        FilledButton(
          onPressed: () => Navigator.of(dialogContext).pop(controller.text),
          child: const Text('Confirm'),
        ),
      ],
    ),
  );
}

/// The one success-dialog implementation for simple confirmations (distinct from the fuller
/// SuccessCard used inline on the collection-confirmation screen).
Future<void> showSuccessDialog(BuildContext context, String message, {String title = 'Done'}) {
  return showDialog<void>(
    context: context,
    builder: (_) => AlertDialog(
      icon: Container(
        width: 48,
        height: 48,
        decoration: const BoxDecoration(color: MicrofiColors.secondary, shape: BoxShape.circle),
        child: const Icon(Icons.check, color: Colors.white, size: 26),
      ),
      title: Text(title, textAlign: TextAlign.center),
      content: Text(message, textAlign: TextAlign.center),
      actions: [
        Center(
          child: FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('OK'),
          ),
        ),
      ],
    ),
  );
}
