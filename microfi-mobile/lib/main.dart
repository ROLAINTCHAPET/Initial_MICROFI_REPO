import 'package:flutter/material.dart';
import 'core/app_background.dart';
import 'core/app_theme.dart';
import 'core/jwt.dart';
import 'core/locale_preference.dart';
import 'core/session_entry.dart';
import 'core/session_storage.dart';
import 'features/auth/role_select_screen.dart';
import 'features/client/client_session_entry.dart';
import 'l10n/app_localizations.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await LocalePreference.instance.load();
  runApp(const MicrofiAgentApp());
}

class MicrofiAgentApp extends StatelessWidget {
  const MicrofiAgentApp({super.key});

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<Locale>(
      valueListenable: LocalePreference.instance.notifier,
      builder: (context, locale, _) {
        return MaterialApp(
          title: 'Microfi',
          debugShowCheckedModeBanner: false,
          theme: microfiTheme,
          // No dark mode has been designed for this app — force light regardless of the device's
          // system theme setting, rather than leaving it to follow ThemeMode.system's default.
          themeMode: ThemeMode.light,
          locale: locale,
          supportedLocales: AppLocalizations.supportedLocales,
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          // `builder` wraps whatever the Navigator renders, at every route, permanently — unlike
          // wrapping `home:` directly, which only covered the very first screen. Every subsequent
          // Navigator.push/pushReplacement (i.e. nearly the whole app) opens as a new, sibling route
          // that isn't nested inside `home`'s widget subtree, so it silently lost the background.
          builder: (context, child) => AppBackground(child: child!),
          home: const _SessionGate(),
        );
      },
    );
  }
}

/// Restores a still-valid session on cold start, so the agent or client doesn't have to log in
/// again on every app launch — same JWT the backend already issues and validates, just persisted
/// locally. The token's own `principalType` claim (AGENT vs CLIENT) decides which shell to
/// restore into — the agent and the client share this one app and one login entry point
/// (RoleSelectScreen), not separate installs.
class _SessionGate extends StatefulWidget {
  const _SessionGate();

  @override
  State<_SessionGate> createState() => _SessionGateState();
}

class _SessionGateState extends State<_SessionGate> {
  final _sessionStorage = SessionStorage();
  bool _checked = false;
  String? _token;
  String? _principalType;

  @override
  void initState() {
    super.initState();
    _restore();
  }

  Future<void> _restore() async {
    final token = await _sessionStorage.readToken();
    if (token != null) {
      try {
        final claims = decodeJwtPayload(token);
        final exp = claims['exp'] as int?;
        final expiresAt = exp != null ? DateTime.fromMillisecondsSinceEpoch(exp * 1000) : null;
        if (expiresAt != null && expiresAt.isAfter(DateTime.now())) {
          _token = token;
          _principalType = claims['principalType'] as String?;
        } else {
          await _sessionStorage.clear();
        }
      } catch (_) {
        await _sessionStorage.clear();
      }
    }
    if (mounted) setState(() => _checked = true);
  }

  @override
  Widget build(BuildContext context) {
    if (!_checked) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_token != null && _principalType == 'CLIENT') {
      return ClientSessionEntry(token: _token!);
    }
    if (_token != null && _principalType == 'AGENT') {
      return SessionEntry(token: _token!);
    }
    return const RoleSelectScreen();
  }
}
