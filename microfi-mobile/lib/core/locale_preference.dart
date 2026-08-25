import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Persists the agent's/client's chosen display language across app restarts, and exposes it as
/// a [ValueListenable] so [MaterialApp] can rebuild live the moment Settings changes it — no app
/// restart needed. Defaults to French: it's the one official language shared by all 6 CEMAC
/// member states (Cameroon, Gabon, Congo, Chad, CAR, Equatorial Guinea), unlike English, which is
/// only co-official in Cameroon.
class LocalePreference {
  static const _key = 'app_locale';
  static const defaultLocale = Locale('fr');

  LocalePreference._();
  static final instance = LocalePreference._();

  final ValueNotifier<Locale> notifier = ValueNotifier(defaultLocale);
  bool _loaded = false;

  Future<void> load() async {
    if (_loaded) return;
    _loaded = true;
    final prefs = await SharedPreferences.getInstance();
    final saved = prefs.getString(_key);
    if (saved != null) {
      notifier.value = Locale(saved);
    }
  }

  Future<void> setLocale(Locale locale) async {
    notifier.value = locale;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, locale.languageCode);
  }
}
