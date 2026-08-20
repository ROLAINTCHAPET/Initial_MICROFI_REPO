import 'package:flutter/material.dart';
import 'design_tokens.dart';

/// Microfi Status Cards Library (Graphical Design/agent/microfi_status_interaction_library) —
/// only the states this app actually drives are implemented here; the mock's token-validation
/// chips have no real backend concept behind them (no device-token lifecycle), so they're
/// deliberately left out rather than shipped as decoration with fabricated data.
class SuccessCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final String amountLabel;
  final String amountValue;

  const SuccessCard({super.key, required this.title, required this.subtitle, required this.amountLabel, required this.amountValue});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(MicrofiSpacing.card),
      decoration: BoxDecoration(
        color: MicrofiColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        border: Border.all(color: MicrofiColors.outlineVariant, width: MicrofiBorders.width),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 40,
                height: 40,
                decoration: const BoxDecoration(color: MicrofiColors.secondary, shape: BoxShape.circle),
                child: const Icon(Icons.check_circle, color: MicrofiColors.onSecondary, size: 22),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 15, color: MicrofiColors.secondary)),
                    const SizedBox(height: 2),
                    Text(subtitle, style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
                  ],
                ),
              ),
            ],
          ),
          const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: Divider(color: MicrofiColors.outlineVariant, height: 1)),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(amountLabel, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
              Text(amountValue, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w700, color: MicrofiColors.primary)),
            ],
          ),
        ],
      ),
    );
  }
}

/// "Escrow Ceiling Reached" alert — a hard stop, not just a warning: the server will reject the
/// submit outright (BR-03) once cumulative cash-in-hand would exceed the ceiling.
class EscrowCeilingReachedCard extends StatelessWidget {
  final String message;
  final VoidCallback? onContactBranch;

  const EscrowCeilingReachedCard({super.key, required this.message, this.onContactBranch});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(MicrofiSpacing.card),
      decoration: BoxDecoration(
        color: MicrofiColors.errorContainer.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        border: Border.all(color: MicrofiColors.errorContainer),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: const BoxDecoration(color: MicrofiColors.error, shape: BoxShape.circle),
                child: const Icon(Icons.lock, color: MicrofiColors.onError, size: 18),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Padding(
                      padding: EdgeInsets.only(top: 2),
                      child: Text('Escrow Ceiling Reached', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: MicrofiColors.error)),
                    ),
                    const SizedBox(height: 3),
                    Text(message, style: const TextStyle(fontSize: 12, color: MicrofiColors.onSurfaceVariant)),
                  ],
                ),
              ),
            ],
          ),
          if (onContactBranch != null) ...[
            const SizedBox(height: 10),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: onContactBranch,
                icon: const Icon(Icons.call, size: 16),
                label: const Text('Contact Branch', style: TextStyle(fontSize: 13)),
                style: FilledButton.styleFrom(
                  backgroundColor: MicrofiColors.secondary,
                  foregroundColor: Colors.white,
                  minimumSize: const Size.fromHeight(42),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// "Offline Mode" banner — shown whenever the device is offline, or there are collections queued
/// locally that haven't synced yet (FR-07). Matches the status-library mock exactly.
class OfflineBanner extends StatelessWidget {
  final int pendingCount;
  final bool syncing;
  final VoidCallback? onSyncNow;

  const OfflineBanner({super.key, required this.pendingCount, this.syncing = false, this.onSyncNow});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(MicrofiSpacing.card),
      decoration: BoxDecoration(
        color: MicrofiColors.tertiaryFixed.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(MicrofiRadius.md),
        border: Border.all(color: MicrofiColors.tertiaryFixedDim),
      ),
      child: Row(
        children: [
          Container(
            width: 32,
            height: 32,
            decoration: const BoxDecoration(color: MicrofiColors.tertiaryFixedDim, shape: BoxShape.circle),
            child: const Icon(Icons.schedule, color: MicrofiColors.onTertiaryFixedVariant, size: 16),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Offline Mode', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13, color: MicrofiColors.onTertiaryFixedVariant)),
                Text(
                  pendingCount == 1 ? '1 collection pending sync' : '$pendingCount collections pending sync',
                  style: const TextStyle(fontSize: 11, color: MicrofiColors.onSurfaceVariant),
                ),
              ],
            ),
          ),
          if (onSyncNow != null)
            OutlinedButton(
              onPressed: syncing ? null : onSyncNow,
              style: OutlinedButton.styleFrom(
                foregroundColor: MicrofiColors.onTertiaryFixedVariant,
                side: const BorderSide(color: MicrofiColors.tertiaryFixedDim),
                minimumSize: const Size(0, 34),
                padding: const EdgeInsets.symmetric(horizontal: 12),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(MicrofiRadius.full)),
              ),
              child: syncing
                  ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('Sync Now', style: TextStyle(fontSize: 12)),
            ),
        ],
      ),
    );
  }
}
