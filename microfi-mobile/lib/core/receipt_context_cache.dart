import 'package:shared_preferences/shared_preferences.dart';

/// The two non-sensitive, rarely-changing fields an offline receipt needs beyond what the
/// collection screen already knows locally (client, amount, denominations, agent identity) —
/// cached opportunistically so composing a receipt while offline never needs a network call.
/// Not secrets, so plain SharedPreferences rather than flutter_secure_storage.
class ReceiptContext {
  final String mfiName;
  final String branchName;

  ReceiptContext({required this.mfiName, required this.branchName});
}

class ReceiptContextCache {
  static const _mfiNameKey = 'receipt_context_mfi_name';
  static const _branchNameKey = 'receipt_context_branch_name';

  Future<void> save({required String mfiName, required String branchName}) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_mfiNameKey, mfiName);
    await prefs.setString(_branchNameKey, branchName);
  }

  /// Null only before the app has ever successfully been online long enough to fetch these once
  /// — practically unreachable, since both are fetched on every Home load, but a receipt composed
  /// in that narrow window falls back to sensible defaults rather than failing to compose at all.
  Future<ReceiptContext?> read() async {
    final prefs = await SharedPreferences.getInstance();
    final mfiName = prefs.getString(_mfiNameKey);
    final branchName = prefs.getString(_branchNameKey);
    if (mfiName == null || branchName == null) return null;
    return ReceiptContext(mfiName: mfiName, branchName: branchName);
  }
}
