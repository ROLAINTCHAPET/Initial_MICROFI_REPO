import 'package:flutter/material.dart';
import 'design_tokens.dart';
import 'locale_preference.dart';
import '../l10n/app_localizations.dart';

/// Small "EN / FR" toggle — placed on the role-select screen (reachable before any login) and on
/// the agent's profile screen (reachable afterwards), so the choice is never more than one tap
/// away regardless of session state. Switching rebuilds the whole app live via
/// [LocalePreference]'s [ValueListenable] — no restart.
class LanguagePickerButton extends StatelessWidget {
  const LanguagePickerButton({super.key});

  @override
  Widget build(BuildContext context) {
    final current = Localizations.localeOf(context).languageCode;
    return PopupMenuButton<String>(
      tooltip: AppLocalizations.of(context)!.languagePickerTitle,
      initialValue: current,
      onSelected: (code) => LocalePreference.instance.setLocale(Locale(code)),
      itemBuilder: (context) {
        final l10n = AppLocalizations.of(context)!;
        return [
          PopupMenuItem(value: 'en', child: Text(l10n.languageEnglish)),
          PopupMenuItem(value: 'fr', child: Text(l10n.languageFrench)),
        ];
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
          borderRadius: BorderRadius.circular(MicrofiRadius.full),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.language, size: 16, color: MicrofiColors.primary),
            const SizedBox(width: 4),
            Text(current.toUpperCase(), style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
          ],
        ),
      ),
    );
  }
}
