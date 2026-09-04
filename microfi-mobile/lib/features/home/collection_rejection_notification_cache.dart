import 'package:shared_preferences/shared_preferences.dart';

/// Tracks which decided (APPROVED/DENIED) collection-rejection request ids an agent has already
/// been shown a notification for — persisted (not just in-memory), same reasoning as
/// SosAckNotificationCache: HomeScreen gets rebuilt from scratch on every tab switch, so an
/// in-memory-only "was this pending a moment ago" check loses the transition if the agent is on
/// another tab when a manager/admin decides their request. Not sensitive data, so plain
/// SharedPreferences rather than flutter_secure_storage.
class CollectionRejectionNotificationCache {
  final String agentId;

  CollectionRejectionNotificationCache(this.agentId);

  String get _key => 'collection_rejection_decided_notified_$agentId';

  /// Returns the ids that are decided now but weren't the last time this ran for this agent —
  /// genuinely new since the last check, not history from before this feature existed or from
  /// before the agent's very first check on this device. Also persists the new baseline, so the
  /// same decision is never surfaced twice.
  Future<List<String>> diffNewlyDecided(List<String> currentlyDecidedIds) async {
    final prefs = await SharedPreferences.getInstance();
    final stored = prefs.getStringList(_key);
    await prefs.setStringList(_key, currentlyDecidedIds);

    if (stored == null) {
      return const [];
    }
    final previouslyKnown = stored.toSet();
    return currentlyDecidedIds.where((id) => !previouslyKnown.contains(id)).toList();
  }
}
