import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

/// Stands in for the real SSAID (Android) / Keychain UUID (iOS) read discussed for device
/// binding: a stable identifier captured automatically in the background, never typed by the
/// agent. Generated once and persisted locally — stable across app restarts on the same install,
/// and (deliberately) changes on reinstall or a new phone, which is exactly when a fresh device
/// binding is supposed to happen (see AuthenticationController#login's auto-bind-on-first-login).
class DeviceIdService {
  static const _key = 'device_id';

  Future<String> getDeviceId() async {
    final prefs = await SharedPreferences.getInstance();
    final existing = prefs.getString(_key);
    if (existing != null && existing.isNotEmpty) return existing;

    final generated = const Uuid().v4();
    await prefs.setString(_key, generated);
    return generated;
  }
}
