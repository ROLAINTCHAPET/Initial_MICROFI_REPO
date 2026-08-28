import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persists the agent's/client's chosen display language across app restarts, and exposes it as
/// a [ValueListenable] so [MaterialApp] can rebuild live the moment Settings changes it — no app
/// restart needed. [notifier] starts on French only as a rendering fallback for the brief window
/// before [load] resolves — [hasChosen] is what actually gates whether the first-launch
/// [LanguageSelectScreen] prompt is shown; the user is always asked explicitly, never left on a
/// silent default.
class LocalePreference {
  static const _key = 'app_locale';
  static const defaultLocale = Locale('fr');

  LocalePreference._();
  static final instance = LocalePreference._();

  final ValueNotifier<Locale> notifier = ValueNotifier(defaultLocale);
  bool _loaded = false;
  bool hasChosen = false;

  Future<void> load() async {
    if (_loaded) return;
    _loaded = true;
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_key);
    if (saved != null) {
      notifier.value = Locale(saved);
      hasChosen = true;
    }
  }

  Future<void> setLocale(Locale locale) async {
    notifier.value = locale;
    hasChosen = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, locale.languageCode);
  }
}
