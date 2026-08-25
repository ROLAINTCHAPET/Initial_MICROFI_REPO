import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:microfi_mobile/features/auth/login_screen.dart';
import 'package:microfi_mobile/l10n/app_localizations.dart';

// Tests LoginScreen directly rather than through MicrofiAgentApp/_SessionGate: the app's startup
// path reads flutter_secure_storage on a real platform channel, which has nothing to answer it in
// the widget-test host (no keyring/DBus here) and hangs pumpAndSettle. That's an environment gap,
// not something these tests are meant to exercise — they cover the login form itself.
//
// A bare `MaterialApp(home: ...)` has no AppLocalizations delegate, so `AppLocalizations.of(context)!`
// (which LoginScreen calls) would throw — these assertions check literal English text, so the
// locale is pinned to English explicitly, independent of whatever LocalePreference happens to hold.
Widget _wrapped(Widget child) => MaterialApp(
      locale: const Locale('en'),
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      home: child,
    );

void main() {
  testWidgets('shows the agent login form', (WidgetTester tester) async {
    await tester.pumpWidget(_wrapped(const LoginScreen()));

    expect(find.text('Username'), findsOneWidget);
    expect(find.text('Password'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Sign In'), findsOneWidget);
  });

  testWidgets('rejects submission with empty fields', (WidgetTester tester) async {
    await tester.pumpWidget(_wrapped(const LoginScreen()));

    await tester.tap(find.widgetWithText(FilledButton, 'Sign In'));
    await tester.pump();

    expect(find.text('Required'), findsWidgets);
  });

  testWidgets('accepts a filled-in username and password without validation errors', (WidgetTester tester) async {
    await tester.pumpWidget(_wrapped(const LoginScreen()));

    await tester.enterText(find.widgetWithText(TextFormField, 'Username'), 'agt.dupont');
    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), 'password123');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign In'));
    await tester.pump();

    expect(find.text('Required'), findsNothing);
  });
}
