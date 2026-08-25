import 'package:shared_preferences/shared_preferences.dart';

/// Tracks which acknowledged SOS ids an agent has already been shown a confirmation for —
/// persisted (not just in-memory), because HomeScreen gets rebuilt from scratch on every tab
/// switch (see AppShell's own doc comment: tabs are rebuilt, not IndexedStack-preserved). An
/// in-memory-only "was this pending a moment ago" check loses the transition entirely if the
/// agent glances at another tab while their SOS is still pending and it gets acknowledged while
/// they're away — this is what actually failed before. Not sensitive data, so plain
/// SharedPreferences rather than flutter_secure_storage.
class SosAckNotificationCache {
  final String agentId;

  SosAckNotificationCache(this.agentId);

  String get _key => 'sos_ack_notified_$agentId';

  /// Returns the ids that are acknowledged now but weren't the last time this ran for this agent
  /// — genuinely new since the last check, not history from before this feature existed or from
  /// before the agent's very first check on this device. Also persists the new baseline.
  Future<List<String>> diffNewlyAcknowledged(List<String> currentlyAcknowledgedIds) async {
    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getStringList(_key);
    await prefs.setStringList(_key, currentlyAcknowledgedIds);

    if (stored == null) {
      // First time this has ever run for this agent on this device — treat every already-
      // acknowledged event as known history, not something to announce right now.
      return const [];
    }
    final previouslyKnown = stored.toSet();
    return currentlyAcknowledgedIds.where((id) => !previouslyKnown.contains(id)).toList();
  }
}
