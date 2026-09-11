import 'package:flutter_test/flutter_test.dart';
import 'package:microfi_mobile/core/schedule_window.dart';

void main() {
  group('isWithinScheduleWindow', () {
    test('true when now falls inside the configured window', () {
      final now = DateTime(2026, 9, 10, 10, 0);
      expect(isWithinScheduleWindow('08:00:00', '17:00:00', now), isTrue);
    });

    test('false when now is before opening time', () {
      final now = DateTime(2026, 9, 10, 7, 59);
      expect(isWithinScheduleWindow('08:00:00', '17:00:00', now), isFalse);
    });

    test('false when now is at or after closing time', () {
      final now = DateTime(2026, 9, 10, 17, 0);
      expect(isWithinScheduleWindow('08:00:00', '17:00:00', now), isFalse);
    });

    test('true right at opening time (inclusive lower bound)', () {
      final now = DateTime(2026, 9, 10, 8, 0);
      expect(isWithinScheduleWindow('08:00:00', '17:00:00', now), isTrue);
    });

    test('true when no schedule is configured at all (both null)', () {
      final now = DateTime(2026, 9, 10, 3, 0);
      expect(isWithinScheduleWindow(null, null, now), isTrue);
    });

    test('true when only one side is configured (malformed data)', () {
      final now = DateTime(2026, 9, 10, 3, 0);
      expect(isWithinScheduleWindow('08:00:00', null, now), isTrue);
      expect(isWithinScheduleWindow(null, '17:00:00', now), isTrue);
    });

    test('true when the time strings are unparsable', () {
      final now = DateTime(2026, 9, 10, 3, 0);
      expect(isWithinScheduleWindow('not-a-time', '17:00:00', now), isTrue);
    });
  });
}
