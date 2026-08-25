import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

/// Backs the flutter_secure_storage platform channel with a plain in-memory map, so code that
/// depends on FlutterSecureStorage can be unit-tested here instead of only on a real device — see
/// widget_test.dart's note on why the real channel just hangs in this host (no keyring/DBus).
/// Call [install] in a `setUp` and [uninstall] in the matching `tearDown`.
class MockSecureStorage {
  static const _channel = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final Map<String, String> _store = {};

  void install() {
    TestWidgetsFlutterBinding.ensureInitialized();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_channel, (call) async {
      final args = (call.arguments as Map?)?.cast<String, dynamic>() ?? {};
      switch (call.method) {
        case 'write':
          _store[args['key'] as String] = args['value'] as String;
          return null;
        case 'read':
          return _store[args['key'] as String];
        case 'delete':
          _store.remove(args['key'] as String);
          return null;
        case 'deleteAll':
          _store.clear();
          return null;
        case 'containsKey':
          return _store.containsKey(args['key'] as String);
        case 'readAll':
          return _store;
        default:
          return null;
      }
    });
  }

  void uninstall() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(_channel, null);
    _store.clear();
  }
}
