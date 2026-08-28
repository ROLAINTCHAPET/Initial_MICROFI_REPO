import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/locale_preference.dart';

/// Shown exactly once, before [RoleSelectScreen], on the very first launch — the language is
/// never assumed. Deliberately doesn't read [AppLocalizations]: no language has been chosen yet,
/// so every label here is self-naming in its own language rather than translated.
class LanguageSelectScreen extends StatelessWidget {
  final VoidCallback onSelected;

  const LanguageSelectScreen({super.key, required this.onSelected});

  Future<void> _choose(Locale locale) async {
    await LocalePreference.instance.setLocale(locale);
    onSelected();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Container(
                    width: 52,
                    height: 52,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: MicrofiColors.primary,
                      borderRadius: BorderRadius.circular(MicrofiRadius.md),
                    ),
                    child: const Icon(Icons.language, color: Colors.white, size: 26),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'Choisissez votre langue',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                  ),
                  const SizedBox(height: 2),
                  const Text(
                    'Choose your language',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 14, color: MicrofiColors.onSurfaceVariant),
                  ),
                  const SizedBox(height: 24),
                  _LanguageCard(label: 'Français', onTap: () => _choose(const Locale('fr'))),
                  const SizedBox(height: 12),
                  _LanguageCard(label: 'English', onTap: () => _choose(const Locale('en'))),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _LanguageCard extends StatelessWidget {
  final String label;
  final VoidCallback onTap;

  const _LanguageCard({required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: MicrofiColors.surfaceContainerLowest,
      borderRadius: BorderRadius.circular(MicrofiRadius.md),
      child: InkWell(
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(MicrofiSpacing.card + 2),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(MicrofiRadius.md),
            border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
          ),
          child: Row(
            children: [
              Expanded(
                child: Text(label, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
              ),
              const Icon(Icons.chevron_right, color: MicrofiColors.outline),
            ],
          ),
        ),
      ),
    );
  }
}
