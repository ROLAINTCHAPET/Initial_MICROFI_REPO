import 'package:flutter/material.dart';
import '../../core/design_tokens.dart';
import '../../core/language_picker.dart';
import '../../l10n/app_localizations.dart';
import '../client/client_login_screen.dart';
import 'login_screen.dart';

/// The agent and the client share this one app — this screen is what makes the login adapt to
/// that reality, instead of assuming every install is a field agent's device.
class RoleSelectScreen extends StatelessWidget {
  const RoleSelectScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: SafeArea(
        child: Stack(
          children: [
            Center(
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
                        child: const Icon(Icons.account_balance, color: Colors.white, size: 26),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        l10n.appName,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700, color: MicrofiColors.primary),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        l10n.roleSelectPrompt,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 13, color: MicrofiColors.onSurfaceVariant),
                      ),
                      const SizedBox(height: 24),
                      _RoleCard(
                        icon: Icons.badge_outlined,
                        title: l10n.roleFieldAgentTitle,
                        subtitle: l10n.roleFieldAgentSubtitle,
                        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const LoginScreen())),
                      ),
                      const SizedBox(height: 12),
                      _RoleCard(
                        icon: Icons.menu_book_outlined,
                        title: l10n.roleClientTitle,
                        subtitle: l10n.roleClientSubtitle,
                        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ClientLoginScreen())),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const Positioned(top: 8, right: 8, child: LanguagePickerButton()),
          ],
        ),
      ),
    );
  }
}

class _RoleCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  const _RoleCard({required this.icon, required this.title, required this.subtitle, required this.onTap});

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
              Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(color: MicrofiColors.primaryContainer.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(MicrofiRadius.sm)),
                child: Icon(icon, color: MicrofiColors.primary, size: 20),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
                    const SizedBox(height: 2),
                    Text(subtitle, style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: MicrofiColors.outline),
            ],
          ),
        ),
      ),
    );
  }
}
