import 'dart:io';

import 'package:android_id/android_id.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:uuid/uuid.dart';

/// The stable identifier device binding checks server-side (still wired through the API/DB as
/// "imei" — see AuthenticationController#login — even though it was never a real hardware IMEI;
/// third-party apps can't read that on Android 10+ regardless).
///
/// Android: Settings.Secure.ANDROID_ID (SSAID) — scoped to (device, user profile, app signing
/// key) since Android 8, so it survives an uninstall/reinstall of this same signed build on the
/// same phone, and only changes on a factory reset or a different signing key. That's the right
/// boundary for this: "same physical device" should stay bound, "different device" shouldn't.
///
/// iOS has no equivalent that survives reinstall — identifierForVendor resets when every app from
/// this vendor is deleted, the opposite of what's needed — so iOS instead gets a UUID generated
/// once and stored in Keychain (via flutter_secure_storage), which does survive an app reinstall.
/// The same Keychain/EncryptedSharedPreferences-backed UUID also covers the rare case where
/// SSAID itself is unavailable on Android.
class DeviceIdService {
  static const _key = 'device_id';
  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();

  Future<String> getDeviceId() async {
    if (Platform.isAndroid) {
      try {
        final ssaid = await const AndroidId().getId();
        if (ssaid != null && ssaid.isNotEmpty) return ssaid;
      } catch (_) {
        // Falls through to the persisted-UUID fallback below.
      }
    }

    final existing = await _secureStorage.read(key: _key);
    if (existing != null && existing.isNotEmpty) return existing;

    final generated = const Uuid().v4();
    await _secureStorage.write(key: _key, value: generated);
    return generated;
  }
}
